package ru.wilyfox.client.profiler;

/** Monotonic time; recovery and cooldown avoid repeated captures of a permanently slow client. */
final class LowFpsCapturePolicy {
    static final long CAPTURE_MILLIS = 15_000;
    static final long COOLDOWN_MILLIS = 600_000;
    private boolean armed = true;
    private long nextCaptureAt;
    private long recoveredSince = -1;

    boolean shouldStart(long now, int fps, boolean active) {
        if (!active) { recoveredSince = -1; return false; }
        if (fps >= 20) {
            if (recoveredSince < 0) recoveredSince = now;
            if (now - recoveredSince >= 5_000) armed = true;
            return false;
        }
        recoveredSince = -1;
        if (!armed || now < nextCaptureAt || fps < 0 || fps >= 15) return false;
        armed = false; nextCaptureAt = now + COOLDOWN_MILLIS;
        return true;
    }
}
