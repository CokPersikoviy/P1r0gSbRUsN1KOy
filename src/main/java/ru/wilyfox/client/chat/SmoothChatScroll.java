package ru.wilyfox.client.chat;

/** Time-based interpolation in wrapped lines; independent of FPS and widget scale. */
public final class SmoothChatScroll {
    private static final long DURATION = 140_000_000L;
    private double from, target;
    private long started;

    public double value(long now) {
        double t = Math.clamp((double) (now - started) / DURATION, 0, 1);
        double remaining = 1 - t;
        return target + (from - target) * remaining * remaining * remaining;
    }
    public void moveTo(double value, long now) {
        from = value(now); target = value; started = now;
    }
    public void reset(double value) { from = target = value; }
    public void shift(double delta) { from += delta; target += delta; }
    public void limit(double maximum) {
        from = Math.clamp(from, 0, maximum); target = Math.clamp(target, 0, maximum);
    }
}
