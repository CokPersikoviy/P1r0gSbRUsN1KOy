package ru.wilyfox.client.highlight;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class GoldenCrystalDiscoveryTracker {
    private final long retentionTicks;
    private final Map<Long, Long> lastSeenTicks = new LinkedHashMap<>();

    GoldenCrystalDiscoveryTracker(long retentionTicks) {
        this.retentionTicks = Math.max(0L, retentionTicks);
    }

    Set<Long> update(Set<Long> visiblePositions, long gameTime) {
        lastSeenTicks.entrySet().removeIf(entry -> gameTime - entry.getValue() > retentionTicks);

        Set<Long> discovered = new LinkedHashSet<>();
        for (long position : visiblePositions) {
            if (!lastSeenTicks.containsKey(position)) {
                discovered.add(position);
            }
            lastSeenTicks.put(position, gameTime);
        }
        return discovered;
    }

    void clear() {
        lastSeenTicks.clear();
    }
}
