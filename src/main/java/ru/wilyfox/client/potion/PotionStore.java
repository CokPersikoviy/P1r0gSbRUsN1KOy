package ru.wilyfox.client.potion;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.protocol.DwPotionTimerEntry;
import ru.wilyfox.client.protocol.DwPotionTypeEntry;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PotionStore {
    private static final Map<Integer, PotionType> MANUAL_TYPES = Map.of(
            15, new PotionType(15, 0, "Зелье добычи")
    );

    private final Map<Integer, PotionType> types = new LinkedHashMap<>();
    private final Map<Integer, PotionState> states = new LinkedHashMap<>();
    private final Map<Integer, Long> cooldownEndsAt = new LinkedHashMap<>();
    private final Map<Integer, ItemStack> iconCache = new LinkedHashMap<>();

    public void replaceTypes(List<DwPotionTypeEntry> entries) {
        types.clear();
        iconCache.clear();

        for (DwPotionTypeEntry entry : entries) {
            types.put(entry.id(), new PotionType(entry.id(), entry.modelId(), entry.name()));
        }
    }

    public void applyUpdate(List<DwPotionTimerEntry> entries) {
        long now = System.currentTimeMillis();
        cleanup(now);

        for (DwPotionTimerEntry entry : entries) {
            long remaining = entry.remainedMillis();
            int quality = entry.quality();

            if (quality <= 0) {
                states.remove(entry.id());
                continue;
            }
            if (remaining <= 0L) {
                // Keep the previous deadline during the configured negative-countdown grace period.
                continue;
            }

            states.put(entry.id(), new PotionState(entry.id(), quality, now + remaining));
        }
    }

    public void applyCooldownUpdate(Map<Integer, Long> cooldowns) {
        long now = System.currentTimeMillis();
        cleanupCooldowns(now);

        for (Map.Entry<Integer, Long> entry : cooldowns.entrySet()) {
            long remainingMillis = entry.getValue();
            if (remainingMillis > 0L) {
                cooldownEndsAt.put(entry.getKey(), now + remainingMillis);
            }
        }
    }

    public Map<Integer, Long> getCooldownsRemaining() {
        long now = System.currentTimeMillis();
        cleanupCooldowns(now);

        Map<Integer, Long> remaining = new LinkedHashMap<>();
        cooldownEndsAt.forEach((id, endsAt) -> remaining.put(id, Math.max(0L, endsAt - now)));
        return Map.copyOf(remaining);
    }

    public List<ActivePotionEntry> getActiveEntries() {
        long now = System.currentTimeMillis();
        cleanup(now);

        List<ActivePotionEntry> result = new ArrayList<>();
        for (PotionState state : states.values()) {
            PotionType type = resolveType(state.id());
            String name = type != null && type.name() != null && !type.name().isBlank()
                    ? sanitizePotionName(type.name())
                    : "Зелье #" + state.id();
            ItemStack icon = type != null ? getOrCreateIcon(type) : new ItemStack(Items.POTION);

            result.add(new ActivePotionEntry(
                    state.id(),
                    name,
                    state.quality(),
                    state.endsAt() - now,
                    icon
            ));
        }

        result.sort(Comparator
                .comparingLong(ActivePotionEntry::remainingMillis)
                .thenComparingInt(ActivePotionEntry::id));
        return result;
    }

    public boolean hasActiveEntries() {
        cleanup(System.currentTimeMillis());
        return !states.isEmpty();
    }

    public void clear() {
        types.clear();
        states.clear();
        cooldownEndsAt.clear();
        iconCache.clear();
    }

    private void cleanup(long now) {
        long grace = graceMs();
        states.entrySet().removeIf(entry -> now - entry.getValue().endsAt() >= grace);
    }

    private void cleanupCooldowns(long now) {
        cooldownEndsAt.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static long graceMs() {
        return Math.max(0, ConfigManager.get().potionTimers.belowZeroSeconds) * 1000L;
    }

    private PotionType resolveType(int id) {
        PotionType type = types.get(id);
        return type != null ? type : MANUAL_TYPES.get(id);
    }

    private ItemStack getOrCreateIcon(PotionType type) {
        return iconCache.computeIfAbsent(type.id(), ignored -> createIcon(type.modelId()));
    }

    private static ItemStack createIcon(int modelId) {
        ItemStack stack = new ItemStack(Items.POTION);
        if (modelId > 0) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) modelId), List.of(), List.of(), List.of()));
        }
        return stack;
    }

    private static String sanitizePotionName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }

        String strippedAmpersand = name.replaceAll("(?i)&[0-9A-FK-ORX]", "");
        return Formatting.stripMinecraftFormatting(strippedAmpersand).trim();
    }

    private record PotionType(int id, int modelId, String name) {
    }

    private record PotionState(int id, int quality, long endsAt) {
    }

    public record ActivePotionEntry(
            int id,
            String name,
            int quality,
            long remainingMillis,
            ItemStack icon
    ) {
    }
}
