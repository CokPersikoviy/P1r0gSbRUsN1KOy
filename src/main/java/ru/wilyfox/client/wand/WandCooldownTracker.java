package ru.wilyfox.client.wand;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WandCooldownTracker {
    private static final long MIN_PROTOCOL_REMAINING_MILLIS = 2_000L;
    private static final long READY_WINDOW_MILLIS = 1_000L;
    private static final Pattern COOLDOWN_PATTERN = Pattern.compile("Перезарядка:\\s*(\\d+(?:[.,]\\d+)?)с\\.?");

    // DiamondWorld's awakened wind staff is named "Посох вихря" in the item/type registry.
    private static final String WIND_STAFF_NAME = "Посох ветра";
    private static final String WIND_STAFF_LOCAL_ALIAS = "Величие гарпии";
    private static final String AWAKENED_WIND_STAFF_NAME = "Посох вихря";

    private final Map<Integer, CooldownState> protocolEntries = new LinkedHashMap<>();
    private final Map<WindStaffVariant, LocalWindCooldown> localWindEntries = new LinkedHashMap<>();
    private final Map<String, WandCooldownEntry> specialEntries = new LinkedHashMap<>();
    private final Map<Integer, StaffType> staffTypes = new LinkedHashMap<>();
    private final LongSupplier clock;

    public WandCooldownTracker() {
        this(System::currentTimeMillis);
    }

    WandCooldownTracker(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void replaceTypes(Map<Integer, String> namesById, Map<Integer, Integer> modelIdsById) {
        staffTypes.clear();
        namesById.forEach((id, name) -> staffTypes.put(
                id,
                new StaffType(name, modelIdsById.getOrDefault(id, id))
        ));
    }

    public void replaceProtocol(Map<Integer, Long> remainingMillisById) {
        long now = clock.getAsLong();
        cleanupElapsed(now);

        // stafftimers is incremental. EvoPlus accepts only newly reported cooldowns above two seconds.
        for (Map.Entry<Integer, Long> entry : remainingMillisById.entrySet()) {
            long remainingMillis = entry.getValue();
            if (remainingMillis <= MIN_PROTOCOL_REMAINING_MILLIS) {
                continue;
            }

            protocolEntries.put(
                    entry.getKey(),
                    createWindow(protocolEntries.get(entry.getKey()), remainingMillis, now)
            );
        }
    }

    public void replaceSpecialCooldown(String key, ItemStack stack, String name, long remainingMillis) {
        if (key == null || key.isBlank()) {
            return;
        }

        long now = clock.getAsLong();
        cleanupElapsed(now);

        WandCooldownEntry previous = specialEntries.get(key);
        CooldownState previousWindow = previous == null
                ? null
                : new CooldownState(previous.startedAt(), previous.endsAt(), previous.durationMillis());
        CooldownState window = createWindow(previousWindow, remainingMillis, now);
        if (window == null) {
            specialEntries.remove(key);
            return;
        }

        specialEntries.put(key, new WandCooldownEntry(
                "special|" + key,
                stack != null ? stack.copy() : new ItemStack(Items.TRIDENT),
                name != null && !name.isBlank() ? name : key,
                window.startedAt(),
                window.endsAt(),
                window.durationMillis()
        ));
    }

    public void trigger(ItemStack stack, Player player, InteractionHand hand) {
        if (stack == null || stack.isEmpty() || player == null || hand == null) {
            return;
        }

        String itemName = stack.getHoverName().getString().trim();
        WindStaffVariant variant = WindStaffVariant.fromLocalName(itemName);
        if (variant == null) {
            return;
        }

        long cooldownMillis = parseCooldownMillis(stack, player);
        if (cooldownMillis <= 0L) {
            return;
        }

        long now = clock.getAsLong();
        cleanupElapsed(now);
        LocalWindCooldown existing = localWindEntries.get(variant);
        if (existing != null && existing.window().endsAt() > now) {
            return;
        }

        localWindEntries.put(variant, new LocalWindCooldown(
                stack.copy(),
                itemName,
                new CooldownState(now, now + cooldownMillis, cooldownMillis)
        ));
    }

    public List<WandCooldownEntry> getActiveEntries() {
        return getActiveEntries(false);
    }

    public List<WandCooldownEntry> getActiveEntries(boolean includeFinalSecond) {
        long now = clock.getAsLong();
        cleanupElapsed(now);

        Map<Integer, WandCooldownEntry> renderedProtocol = new LinkedHashMap<>();
        Map<WindStaffVariant, Integer> protocolWindIds = new LinkedHashMap<>();

        for (Map.Entry<Integer, CooldownState> entry : protocolEntries.entrySet()) {
            CooldownState window = entry.getValue();
            if (!isVisibleStaffWindow(window, now, includeFinalSecond)) {
                continue;
            }

            StaffType type = staffTypes.get(entry.getKey());
            if (type == null) {
                continue;
            }

            WindStaffVariant variant = WindStaffVariant.fromName(type.name());
            String key = "protocol|" + entry.getKey();
            if (variant != null && !protocolWindIds.containsKey(variant)) {
                protocolWindIds.put(variant, entry.getKey());
                key = variant.entryKey();
            }

            renderedProtocol.put(entry.getKey(), new WandCooldownEntry(
                    key,
                    createStaffStack(type.modelId(), type.name()),
                    type.name(),
                    window.startedAt(),
                    window.endsAt(),
                    window.durationMillis()
            ));
        }

        List<WandCooldownEntry> result = new ArrayList<>(renderedProtocol.values());
        for (Map.Entry<WindStaffVariant, LocalWindCooldown> entry : localWindEntries.entrySet()) {
            WindStaffVariant variant = entry.getKey();
            LocalWindCooldown local = entry.getValue();
            if (!isLocalWindVisible(local.window().endsAt(), now)) {
                continue;
            }

            Integer protocolId = protocolWindIds.get(variant);
            if (protocolId == null) {
                result.add(toLocalEntry(variant, local));
                continue;
            }

            WandCooldownEntry protocol = renderedProtocol.get(protocolId);
            if (protocol != null && local.window().endsAt() > protocol.endsAt()) {
                int resultIndex = result.indexOf(protocol);
                WandCooldownEntry merged = new WandCooldownEntry(
                        variant.entryKey(),
                        local.stack().copy(),
                        protocol.name(),
                        local.window().startedAt(),
                        local.window().endsAt(),
                        local.window().durationMillis()
                );
                if (resultIndex >= 0) {
                    result.set(resultIndex, merged);
                }
            }
        }

        result.addAll(specialEntries.values());
        result.sort(Comparator.comparingLong(WandCooldownEntry::endsAt));
        return result;
    }

    public boolean hasActiveEntries() {
        return hasActiveEntries(false);
    }

    public boolean hasActiveEntries(boolean includeFinalSecond) {
        long now = clock.getAsLong();
        cleanupElapsed(now);

        boolean hasProtocol = protocolEntries.entrySet().stream().anyMatch(entry ->
                staffTypes.containsKey(entry.getKey()) && isVisibleStaffWindow(entry.getValue(), now, includeFinalSecond)
        );
        boolean hasLocalWind = localWindEntries.values().stream().anyMatch(entry ->
                isLocalWindVisible(entry.window().endsAt(), now)
        );
        return hasProtocol || hasLocalWind || !specialEntries.isEmpty();
    }

    public static boolean isWindStaffName(String name) {
        return WindStaffVariant.fromLocalName(name) != null;
    }

    public void clear() {
        protocolEntries.clear();
        localWindEntries.clear();
        specialEntries.clear();
        staffTypes.clear();
    }

    private void cleanupElapsed(long now) {
        protocolEntries.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now);
        localWindEntries.entrySet().removeIf(entry -> entry.getValue().window().endsAt() <= now);
        specialEntries.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now);
    }

    private long parseCooldownMillis(ItemStack stack, Player player) {
        List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.of(player.level()), player, TooltipFlag.NORMAL);

        for (Component line : tooltip) {
            String text = line.getString().replace('\u00A0', ' ').trim();
            Matcher matcher = COOLDOWN_PATTERN.matcher(text);
            if (matcher.find()) {
                double seconds = Double.parseDouble(matcher.group(1).replace(',', '.'));
                return Math.max(1L, Math.round(seconds * 1000.0D));
            }
        }

        return -1L;
    }

    private static CooldownState createWindow(CooldownState previous, long remainingMillis, long now) {
        if (remainingMillis <= 0L) {
            return null;
        }

        long duration = previous == null
                ? remainingMillis
                : Math.max(previous.durationMillis(), remainingMillis);
        long startedAt = now - Math.max(0L, duration - remainingMillis);
        return new CooldownState(startedAt, now + remainingMillis, duration);
    }

    private static boolean isVisibleStaffWindow(CooldownState window, long now, boolean includeFinalSecond) {
        long remaining = window.endsAt() - now;
        return remaining > (includeFinalSecond ? 0L : READY_WINDOW_MILLIS);
    }

    static boolean isLocalWindVisible(long endsAt, long now) {
        return endsAt > now;
    }

    private static WandCooldownEntry toLocalEntry(WindStaffVariant variant, LocalWindCooldown local) {
        return new WandCooldownEntry(
                variant.entryKey(),
                local.stack().copy(),
                local.name(),
                local.window().startedAt(),
                local.window().endsAt(),
                local.window().durationMillis()
        );
    }

    private static ItemStack createStaffStack(int modelId, String name) {
        ItemStack stack = new ItemStack(Items.WOODEN_HOE);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) modelId), List.of(), List.of(), List.of()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static String normalizeStaffName(String name) {
        String normalized = Formatting.stripMinecraftFormatting(name == null ? "" : name)
                .replace('\u00A0', ' ')
                .replace('ё', 'е')
                .replace('Ё', 'Е')
                .replaceAll("(?i)&[0-9A-FK-ORX]", "")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (normalized.endsWith(" пробужденный")) {
            normalized = normalized.substring(0, normalized.length() - " пробужденный".length()).trim();
        }
        return normalized;
    }

    private enum WindStaffVariant {
        WIND(WIND_STAFF_NAME, "wind"),
        AWAKENED(AWAKENED_WIND_STAFF_NAME, "awakened-wind");

        private final String normalizedName;
        private final String key;

        WindStaffVariant(String displayName, String key) {
            this.normalizedName = normalizeStaffName(displayName);
            this.key = key;
        }

        static WindStaffVariant fromName(String name) {
            String normalized = normalizeStaffName(name);
            for (WindStaffVariant variant : values()) {
                if (variant.normalizedName.equals(normalized)) {
                    return variant;
                }
            }
            return null;
        }

        static WindStaffVariant fromLocalName(String name) {
            String normalized = normalizeStaffName(name);
            if (normalizeStaffName(WIND_STAFF_LOCAL_ALIAS).equals(normalized)) {
                return WIND;
            }
            return fromName(normalized);
        }

        String entryKey() {
            return "wind|" + key;
        }
    }

    private record StaffType(String name, int modelId) {
    }

    private record CooldownState(long startedAt, long endsAt, long durationMillis) {
    }

    private record LocalWindCooldown(ItemStack stack, String name, CooldownState window) {
    }

    public record WandCooldownEntry(
            String key,
            ItemStack stack,
            String name,
            long startedAt,
            long endsAt,
            long durationMillis
    ) {
        public float progress() {
            long remaining = Math.max(0L, endsAt - System.currentTimeMillis());
            if (durationMillis <= 0L) {
                return 0.0F;
            }

            return remaining / (float) durationMillis;
        }
    }
}
