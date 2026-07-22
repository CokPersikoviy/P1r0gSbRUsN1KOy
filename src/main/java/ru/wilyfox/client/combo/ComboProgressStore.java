package ru.wilyfox.client.combo;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class ComboProgressStore {
    private final LongSupplier clock;
    private volatile Snapshot snapshot = Snapshot.empty();

    public ComboProgressStore() {
        this(System::currentTimeMillis);
    }

    ComboProgressStore(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void updateCombo(double booster, double nextBooster, int blocks, int requiredBlocks) {
        snapshot = new Snapshot(
                true,
                Math.max(1.0D, booster),
                nextBooster,
                blocks,
                requiredBlocks,
                0L
        );
    }

    public void updateBlocks(int blocks) {
        Snapshot current = snapshot;
        if (!current.available()) {
            snapshot = new Snapshot(true, 1.0D, 1.1D, blocks, 1000, 0L);
            return;
        }

        snapshot = new Snapshot(
                true,
                current.booster(),
                current.nextBooster(),
                blocks,
                current.requiredBlocks(),
                0L
        );
    }

    public void setRemainingSeconds(long remainingSeconds) {
        Snapshot current = snapshot;
        long expiryTimestamp = remainingSeconds <= 0L
                ? 0L
                : safeDeadline(clock.getAsLong(), remainingSeconds);
        snapshot = new Snapshot(
                current.available(),
                current.booster(),
                current.nextBooster(),
                current.blocks(),
                current.requiredBlocks(),
                expiryTimestamp
        );
    }

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public void clear() {
        snapshot = Snapshot.empty();
    }

    public record Snapshot(
            boolean available,
            double booster,
            double nextBooster,
            int blocks,
            int requiredBlocks,
            long expiryTimestamp
    ) {
        public static Snapshot empty() {
            return new Snapshot(false, 1.0D, 1.1D, 0, 1000, 0L);
        }

        public double progress() {
            if (requiredBlocks <= 0) {
                return 0.0D;
            }
            return Math.max(0.0D, Math.min(1.0D, blocks / (double) requiredBlocks));
        }

        public boolean completed() {
            return blocks >= requiredBlocks;
        }

        public boolean maxed() {
            return booster == nextBooster;
        }

        public long remainingSeconds(long now) {
            if (expiryTimestamp == 0L || now > expiryTimestamp) {
                return 0L;
            }
            return Math.max(0L, (expiryTimestamp - now) / 1000L);
        }

        public boolean expiring(long now) {
            return remainingSeconds(now) > 0L;
        }
    }

    private static long safeDeadline(long now, long remainingSeconds) {
        if (remainingSeconds > (Long.MAX_VALUE - now) / 1000L) {
            return Long.MAX_VALUE;
        }
        return now + remainingSeconds * 1000L;
    }
}
