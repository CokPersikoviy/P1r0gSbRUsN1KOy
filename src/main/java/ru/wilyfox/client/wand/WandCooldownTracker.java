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
import ru.wilyfox.utils.CooldownWindow;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WandCooldownTracker {
    private static final Pattern COOLDOWN_PATTERN = Pattern.compile("Перезарядка:\\s*(\\d+(?:[.,]\\d+)?)с\\.?");
    private static final String WAND_PREFIX = "Посох ";
    private static final Map<String, String> STAFF_NAME_ALIASES = Map.ofEntries(
            Map.entry(normalizeStaffName("Посох жизни"), normalizeStaffName("Посох регенерации")),
            Map.entry(normalizeStaffName("Посох мощи"), normalizeStaffName("Посох силы")),
            Map.entry(normalizeStaffName("Посох дракона"), normalizeStaffName("Посох энда")),
            Map.entry(normalizeStaffName("Посох пламени"), normalizeStaffName("Посох огня")),
            Map.entry(normalizeStaffName("Посох грома"), normalizeStaffName("Посох молний")),
            Map.entry(normalizeStaffName("Посох разрушения"), normalizeStaffName("Посох шахтера")),
            Map.entry(normalizeStaffName("Посох листопада"), normalizeStaffName("Посох листьев")),
            Map.entry(normalizeStaffName("Посох вихря"), normalizeStaffName("Посох ветра"))
    );
    private static final String WIND_CANONICAL_NAME = normalizeStaffName("Посох ветра");

    private final Map<Integer, WandCooldownEntry> protocolEntries = new LinkedHashMap<>();
    private final Map<String, WandCooldownEntry> localEntries = new LinkedHashMap<>();
    private final Map<String, WandCooldownEntry> specialEntries = new LinkedHashMap<>();
    private final Map<Integer, String> staffNames = new LinkedHashMap<>();
    private final Map<Integer, Integer> staffModelIds = new LinkedHashMap<>();
    private final Map<String, ItemStack> observedStacks = new LinkedHashMap<>();

    public void replaceTypes(Map<Integer, String> namesById, Map<Integer, Integer> modelIdsById) {
        staffNames.clear();
        staffNames.putAll(namesById);
        staffModelIds.clear();
        staffModelIds.putAll(modelIdsById);
    }

    public void replaceProtocol(Map<Integer, Long> remainingMillisById) {
        cleanup();
        // stafftimers arrives incrementally (only the staff whose cooldown just changed), NOT as a
        // full snapshot — so ids absent from this packet are still on cooldown and must be kept.
        // Duplicates from re-awakening (a staff getting a new id mid-cooldown) are collapsed by the
        // canonical-name merge in getActiveEntries(); stale ids fall off via time-based cleanup().
        for (Map.Entry<Integer, Long> entry : remainingMillisById.entrySet()) {
            String name = staffNames.getOrDefault(entry.getKey(), "Staff " + entry.getKey());
            int modelId = staffModelIds.getOrDefault(entry.getKey(), entry.getKey());
            upsertEntry(protocolEntries, entry.getKey(), "protocol|" + entry.getKey(), createStaffStack(modelId, name), name, entry.getValue());

            // Protocol-preferred: the server's value is authoritative, so drop the optimistic local bridge
            // for this staff the moment its FIRST packet arrives — the shown value corrects immediately.
            // The wind staff stays local-driven (its protocol lags) and keeps its local window.
            String canonical = canonicalStaffName(name);
            if (!WIND_CANONICAL_NAME.equals(canonical)) {
                localEntries.remove(canonical);
            }
        }
    }

    public void replaceSpecialCooldown(String key, ItemStack stack, String name, long remainingMillis) {
        if (key == null || key.isBlank()) {
            return;
        }

        cleanup();
        upsertEntry(
                specialEntries,
                key,
                "special|" + key,
                stack != null ? stack.copy() : new ItemStack(Items.TRIDENT),
                name != null && !name.isBlank() ? name : key,
                remainingMillis
        );
    }

    public void trigger(ItemStack stack, Player player, InteractionHand hand) {
        if (stack == null || stack.isEmpty() || player == null || hand == null) {
            return;
        }

        String itemName = stack.getHoverName().getString().trim();
        if (!itemName.startsWith(WAND_PREFIX)) {
            return;
        }

        long cooldownMillis = parseCooldownMillis(stack, player);
        if (cooldownMillis <= 0L) {
            return;
        }

        String canonicalName = canonicalStaffName(itemName);
        observedStacks.put(canonicalName, stack.copy());

        // Start an OPTIMISTIC local cooldown for EVERY staff on use (was wind-only). The `stafftimers`
        // protocol packet is incremental/server-timed and can lag or drop, so a non-wind staff's bar
        // "sometimes doesn't start" until the packet arrives; the local entry starts it instantly, and
        // getActiveEntries() still merges the protocol value on top (keeps the fresher/longer window).
        long now = System.currentTimeMillis();
        WandCooldownEntry existing = localEntries.get(canonicalName);
        if (existing != null && existing.endsAt() > now) {
            return; // already on a local cooldown — don't restart it
        }

        localEntries.put(canonicalName, new WandCooldownEntry(
                "local|" + canonicalName,
                stack.copy(),
                itemName,
                now,
                now + cooldownMillis,
                cooldownMillis
        ));
    }

    public List<WandCooldownEntry> getActiveEntries() {
        cleanup();

        Map<String, WandCooldownEntry> merged = new LinkedHashMap<>();
        for (WandCooldownEntry protocolEntry : protocolEntries.values()) {
            String canonicalName = canonicalStaffName(protocolEntry.name());
            ItemStack observed = observedStacks.get(canonicalName);
            ItemStack stack = observed != null ? observed.copy() : protocolEntry.stack();
            WandCooldownEntry candidate = new WandCooldownEntry(
                    protocolEntry.key(),
                    stack,
                    protocolEntry.name(),
                    protocolEntry.startedAt(),
                    protocolEntry.endsAt(),
                    protocolEntry.durationMillis()
            );

            // Two different protocol ids can briefly resolve to the same canonical name
            // (e.g. an awakening reassigns the id mid-cooldown); keep whichever is fresher
            // instead of letting iteration order decide, so the pair never renders as two entries.
            WandCooldownEntry existing = merged.get(canonicalName);
            if (existing == null || candidate.endsAt() > existing.endsAt()) {
                merged.put(canonicalName, candidate);
            }
        }

        for (Map.Entry<String, WandCooldownEntry> entry : localEntries.entrySet()) {
            WandCooldownEntry localEntry = entry.getValue();
            WandCooldownEntry protocolEntry = merged.get(entry.getKey());
            if (protocolEntry == null || localEntry.endsAt() > protocolEntry.endsAt()) {
                merged.put(entry.getKey(), localEntry);
            }
        }

        for (Map.Entry<String, WandCooldownEntry> entry : specialEntries.entrySet()) {
            merged.put(entry.getKey(), entry.getValue());
        }

        List<WandCooldownEntry> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparingLong(WandCooldownEntry::endsAt));
        return result;
    }

    public boolean hasActiveEntries() {
        cleanup();
        return !protocolEntries.isEmpty() || !localEntries.isEmpty() || !specialEntries.isEmpty();
    }

    public static boolean isWindStaffName(String name) {
        return WIND_CANONICAL_NAME.equals(normalizeStaffName(name));
    }

    public void clear() {
        protocolEntries.clear();
        localEntries.clear();
        specialEntries.clear();
        staffNames.clear();
        staffModelIds.clear();
        observedStacks.clear();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        protocolEntries.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now);
        localEntries.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now);
        specialEntries.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now);
    }

    private long parseCooldownMillis(ItemStack stack, Player player) {
        List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.of(player.level()), player, TooltipFlag.NORMAL);

        for (Component line : tooltip) {
            String text = line.getString().replace('\u00A0', ' ').trim();
            Matcher matcher = COOLDOWN_PATTERN.matcher(text);
            if (matcher.find()) {
                double seconds = Double.parseDouble(matcher.group(1).replace(',', '.'));
                return Math.max(1L, Math.round(seconds * 1000.0d));
            }
        }

        return -1L;
    }

    private ItemStack createStaffStack(int modelId, String name) {
        ItemStack stack = new ItemStack(Items.WOODEN_HOE);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) modelId), List.of(), List.of(), List.of()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private <K> void upsertEntry(Map<K, WandCooldownEntry> entries, K entryKey, String key, ItemStack stack, String name, long remainingMillis) {
        WandCooldownEntry previous = entries.get(entryKey);
        CooldownWindow previousWindow = previous == null
                ? null
                : new CooldownWindow(previous.startedAt(), previous.endsAt(), previous.durationMillis());

        CooldownWindow window = CooldownWindow.extend(previousWindow, remainingMillis);
        if (window == null) {
            entries.remove(entryKey);
            return;
        }

        entries.put(entryKey, new WandCooldownEntry(
                key,
                stack,
                name,
                window.startedAt(),
                window.endsAt(),
                window.durationMillis()
        ));
    }

    private String canonicalStaffName(String name) {
        String normalized = normalizeStaffName(name);
        return STAFF_NAME_ALIASES.getOrDefault(normalized, normalized);
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
            normalized = normalized.substring(0, normalized.length() - " пробужденный".length());
        }
        return normalized;
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
                return 0.0f;
            }

            return remaining / (float) durationMillis;
        }
    }
}
