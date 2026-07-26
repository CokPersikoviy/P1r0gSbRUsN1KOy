package ru.wilyfox.client.popup;

import ru.wilyfox.client.miner.ActiveMinerInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MinerReturnTracker {
    private final Map<String, MinerGroupSnapshot> previousGroups = new LinkedHashMap<>();

    void prime(List<ActiveMinerInfo> miners) {
        prime(miners, System.nanoTime());
    }

    void prime(List<ActiveMinerInfo> miners, long nowNanos) {
        previousGroups.clear();
        previousGroups.putAll(snapshot(miners, nowNanos));
    }

    List<ActiveMinerInfo> update(List<ActiveMinerInfo> miners) {
        return update(miners, System.nanoTime());
    }

    List<ActiveMinerInfo> update(List<ActiveMinerInfo> miners, long nowNanos) {
        Map<String, MinerGroupSnapshot> currentGroups = snapshot(miners, nowNanos);
        List<ActiveMinerInfo> newlyReturned = new ArrayList<>();

        for (Map.Entry<String, MinerGroupSnapshot> entry : currentGroups.entrySet()) {
            MinerGroupSnapshot previous = previousGroups.get(entry.getKey());
            if (previous == null) {
                continue;
            }

            MinerGroupSnapshot current = entry.getValue();
            int returnCount = Math.max(0, current.returnedMiners().size() - previous.returnedCount());
            for (int i = 0; i < returnCount; i++) {
                newlyReturned.add(current.returnedMiners().get(
                        Math.min(previous.returnedCount() + i, current.returnedMiners().size() - 1)
                ));
            }
        }

        previousGroups.clear();
        previousGroups.putAll(currentGroups);
        return List.copyOf(newlyReturned);
    }

    void reset() {
        previousGroups.clear();
    }

    private static Map<String, MinerGroupSnapshot> snapshot(List<ActiveMinerInfo> miners, long nowNanos) {
        Map<String, MutableMinerGroup> groups = new LinkedHashMap<>();
        if (miners == null) {
            return Map.of();
        }

        for (ActiveMinerInfo miner : miners) {
            if (miner == null) {
                continue;
            }

            MutableMinerGroup group = groups.computeIfAbsent(miner.id(), ignored -> new MutableMinerGroup());
            if (miner.isCompleteAt(nowNanos)) {
                group.returnedMiners.add(miner);
            }
        }

        Map<String, MinerGroupSnapshot> snapshots = new LinkedHashMap<>();
        for (Map.Entry<String, MutableMinerGroup> entry : groups.entrySet()) {
            snapshots.put(entry.getKey(), new MinerGroupSnapshot(List.copyOf(entry.getValue().returnedMiners)));
        }
        return snapshots;
    }

    private static final class MutableMinerGroup {
        private final List<ActiveMinerInfo> returnedMiners = new ArrayList<>();
    }

    private record MinerGroupSnapshot(List<ActiveMinerInfo> returnedMiners) {
        private int returnedCount() {
            return returnedMiners.size();
        }
    }
}
