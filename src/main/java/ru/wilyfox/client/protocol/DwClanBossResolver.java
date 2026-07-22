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
            DwBossType type = bossTypes.get(bossId);
            if (type != null) {
                levels.add(type.level());
            }
        }
        return levels.isEmpty() ? Set.of() : Set.copyOf(levels);
    }
}
