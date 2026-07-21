package ru.wilyfox.utils;

/**
 * Shared cooldown-window math for stores that track "remaining millis" timers pushed by the
 * server (abilities, wands, ...). Centralised so the restart/extend logic cannot silently drift
 * between stores again — a divergent copy of this exact formula is what caused the staff/ability
 * cooldown bugs (one store used {@code now + duration} instead of {@code now + remaining}).
 */
public record CooldownWindow(long startedAt, long endsAt, long durationMillis) {

    /**
     * Produces the window for an incoming cooldown update. {@code previous} may be {@code null}
     * for a fresh entry. Returns {@code null} when the cooldown has elapsed, signalling the caller
     * to drop the entry rather than store an already-expired one.
     */
    public static CooldownWindow extend(CooldownWindow previous, long remainingMillis) {
        long remaining = Math.max(0L, remainingMillis);
        if (remaining <= 0L) {
            return null;
        }

        long now = System.currentTimeMillis();
        long duration = previous != null ? Math.max(previous.durationMillis(), remaining) : remaining;
        long startedAt = now - Math.max(0L, duration - remaining);
        return new CooldownWindow(startedAt, now + remaining, duration);
    }

    public long remainingMillis() {
        return Math.max(0L, endsAt - System.currentTimeMillis());
    }

    public float progress() {
        if (durationMillis <= 0L) {
            return 0.0F;
        }

        return remainingMillis() / (float) durationMillis;
    }
}
