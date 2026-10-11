package ru.wilyfox.client.highlight;

import java.util.LinkedHashSet;
import java.util.Set;

final class GoldenCrystalDiscoveryTracker {
    private final Set<Long> discoveredPositions = new LinkedHashSet<>();

    Set<Long> update(Set<Long> visiblePositions) {
        Set<Long> discovered = new LinkedHashSet<>();
        for (long position : visiblePositions) {
            if (discoveredPositions.add(position)) {
                discovered.add(position);
            }
        }
        return discovered;
    }

    void clear() {
        discoveredPositions.clear();
    }
}
