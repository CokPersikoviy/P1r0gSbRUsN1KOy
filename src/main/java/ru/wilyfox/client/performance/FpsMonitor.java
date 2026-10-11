package ru.wilyfox.client.performance;

import java.util.Arrays;

/** Render-thread only. Bounded rolling frame times; sorting reuses one scratch array. */
public final class FpsMonitor {
    public static final FpsMonitor INSTANCE = new FpsMonitor();
    static final int CAPACITY = 16_384;
    static final long WINDOW_NANOS = 30_000_000_000L;
    private static final long UPDATE_NANOS = 500_000_000L;
    private final long[] endedAt = new long[CAPACITY], durations = new long[CAPACITY], sorted = new long[CAPACITY];
    private int head, count;
    private long totalNanos, lastFrame, lastUpdate;
    private boolean tracking, calculated;
    private Snapshot cached = Snapshot.EMPTY;

    public void recordFrame(long now, boolean active) {
        if (!active) { reset(); return; }
        if (tracking && now > lastFrame) {
            expire(now);
            if (count == CAPACITY) removeOldest();
            int index = (head + count) % CAPACITY;
            endedAt[index] = now; durations[index] = now - lastFrame;
            totalNanos += durations[index]; count++;
        } else if (tracking) reset();
        lastFrame = now; tracking = true;
    }

    public Snapshot snapshot(long now) {
        if (!tracking) return Snapshot.EMPTY;
        expire(now);
        if (count == 0) { calculated = false; return cached = Snapshot.EMPTY; }
        if (calculated && now - lastUpdate < UPDATE_NANOS) return cached;
        for (int i = 0; i < count; i++) sorted[i] = durations[(head + i) % CAPACITY];
        Arrays.sort(sorted, 0, count);
        cached = new Snapshot(1_000_000_000d * count / totalNanos, low(.01), low(.001), count);
        lastUpdate = now; calculated = true;
        return cached;
    }

    private double low(double fraction) {
        int slowest = Math.max(1, (int) Math.ceil(count * fraction));
        long sum = 0;
        for (int i = count - slowest; i < count; i++) sum += sorted[i];
        return 1_000_000_000d * slowest / sum;
    }
    private void expire(long now) {
        while (count > 0 && now - endedAt[head] >= WINDOW_NANOS) removeOldest();
    }
    private void removeOldest() {
        totalNanos -= durations[head]; head = (head + 1) % CAPACITY; count--;
    }
    public void reset() {
        head = count = 0; totalNanos = lastFrame = lastUpdate = 0;
        tracking = calculated = false; cached = Snapshot.EMPTY;
    }
    public record Snapshot(double average, double onePercentLow, double pointOnePercentLow, int sampleCount) {
        static final Snapshot EMPTY = new Snapshot(Double.NaN, Double.NaN, Double.NaN, 0);
    }
}
