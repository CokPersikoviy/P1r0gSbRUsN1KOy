package ru.wilyfox.client.audio;

/** A regular cadence with a strict rolling limit, without adding frame jitter to every interval. */
final class UiSoundLimiter {
    private final long intervalNanos;
    private final long[] recent;
    private boolean played;
    private long nextTick;
    private int first, count;

    UiSoundLimiter(int ticksPerSecond) {
        if (ticksPerSecond <= 0) throw new IllegalArgumentException("Tick rate must be positive");
        intervalNanos = 1_000_000_000L / ticksPerSecond;
        recent = new long[ticksPerSecond];
    }

    boolean allow(long now) {
        while (count > 0 && now - recent[first] >= 1_000_000_000L) {
            first = (first + 1) % recent.length;
            count--;
        }
        if ((played && now < nextTick) || count == recent.length) return false;
        recent[(first + count) % recent.length] = now;
        count++;
        nextTick = !played || now - nextTick >= intervalNanos ? now + intervalNanos : nextTick + intervalNanos;
        played = true;
        return true;
    }
}
