package ru.wilyfox.client.protocol;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class DwClanBossResolver {
    private DwClanBossResolver() {
    }

    static Set<Integer> resolveLevels(DwClanState clan, Map<String, DwBossType> bossTypes) {
        Set<Integer> levels = new LinkedHashSet<>();
        for (String bossId : clan.bossIds()) {
            DwBossType type = findBossType(bossId, bossTypes);
            if (type != null) {
                levels.add(type.level());
            }
        }
        return levels.isEmpty() ? Set.of() : Set.copyOf(levels);
    }

    private static DwBossType findBossType(String bossId, Map<String, DwBossType> bossTypes) {
        if (bossId == null) {
            return null;
        }

        String normalizedId = bossId.trim();
        DwBossType exact = bossTypes.get(normalizedId);
        if (exact != null) {
            return exact;
        }

        for (Map.Entry<String, DwBossType> entry : bossTypes.entrySet()) {
            if (entry.getKey().trim().equalsIgnoreCase(normalizedId)
                    || entry.getValue().id().trim().equalsIgnoreCase(normalizedId)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
