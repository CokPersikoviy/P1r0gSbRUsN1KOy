package ru.wilyfox.client.miner;

import java.util.ArrayList;
import java.util.List;

public class ActiveMinersStore {
    private List<ActiveMinerInfo> miners = new ArrayList<>();

    public void replace(List<ActiveMinerInfo> updatedMiners) {
        miners = new ArrayList<>(updatedMiners);
    }

    public List<ActiveMinerInfo> getAll() {
        return List.copyOf(miners);
    }

    public boolean isEmpty() {
        return miners.isEmpty();
    }

    public void clear() {
        miners.clear();
    }
}
