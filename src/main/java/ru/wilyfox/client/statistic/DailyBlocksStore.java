package ru.wilyfox.client.statistic;

import java.util.OptionalInt;

public final class DailyBlocksStore {
    private long currentBlocks;
    private long windowStartBlocks;
    private int serverBlocksInWindow = -1;
    private boolean hasCurrentBlocks;
    private boolean available;

    public synchronized void update(Integer totalBlocks, OptionalInt blocksIn24Hours) {
        boolean serverWindowChanged = false;
        if (blocksIn24Hours != null && blocksIn24Hours.isPresent()) {
            int sanitized = Math.max(0, blocksIn24Hours.getAsInt());
            serverWindowChanged = serverBlocksInWindow != sanitized;
            serverBlocksInWindow = sanitized;
        }

        if (totalBlocks != null) {
            currentBlocks = Math.max(0L, totalBlocks.longValue());
            hasCurrentBlocks = true;
        }

        if (hasCurrentBlocks && serverBlocksInWindow >= 0 && (!available || serverWindowChanged)) {
            windowStartBlocks = currentBlocks - serverBlocksInWindow;
            available = true;
        }
    }

    public synchronized Snapshot getSnapshot() {
        long blocks = available ? Math.max(0L, currentBlocks - windowStartBlocks) : 0L;
        return new Snapshot(blocks, available);
    }

    public synchronized void clear() {
        currentBlocks = 0L;
        windowStartBlocks = 0L;
        serverBlocksInWindow = -1;
        hasCurrentBlocks = false;
        available = false;
    }

    public record Snapshot(long blocks, boolean available) {
    }
}
