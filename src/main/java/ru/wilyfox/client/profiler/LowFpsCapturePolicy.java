package ru.wilyfox.client.profiler;

/** Monotonic time; confirmation, recovery and cooldown separate sustained drops from isolated spikes. */
final class LowFpsCapturePolicy {
    static final long CONFIRMATION_MILLIS = 5_000;
    static final long CAPTURE_MILLIS = 15_000;
    static final long COOLDOWN_MILLIS = 600_000;
    private boolean armed = true;
    private boolean confirming, recovering, captured;
    private long lowFpsSince, recoveredSince, lastCaptureAt;

    boolean shouldStart(long now, int fps, boolean active) {
        if (!active) {
            confirming = false;
            recovering = false;
            return false;
        }
        if (fps >= 20) {
            if (!recovering) { recoveredSince = now; recovering = true; }
            if (now - recoveredSince >= 5_000) armed = true;
        } else {
            recovering = false;
        }
        if (fps < 0 || fps >= 15 || !armed || captured && now - lastCaptureAt < COOLDOWN_MILLIS) {
            confirming = false;
            return false;
        }
        if (!confirming) {
            lowFpsSince = now;
            confirming = true;
            return false;
        }
        if (now - lowFpsSince < CONFIRMATION_MILLIS) return false;
        armed = false;
        captured = true;
        lastCaptureAt = now;
        confirming = false;
        return true;
    }
}
