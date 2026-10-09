package ru.wilyfox.client.hud.internal;

/** Extra editor scale, independent of the widget's configured scale. */
public final class WidgetSizeTransition {
    public static final long DURATION_NANOS = 200_000_000L;
    private boolean present;
    private float from;
    private long started = Long.MIN_VALUE;
    public WidgetSizeTransition(boolean present) { this.present = present; from = present ? 1 : 0; }
    public void target(boolean present, long now) {
        if (this.present == present) return;
        from = size(now); this.present = present; started = now;
    }
    public boolean present() { return present; }
    public float size(long now) {
        float t = started == Long.MIN_VALUE ? 1 : Math.clamp((float) (now - started) / DURATION_NANOS, 0, 1);
        t = t * t * (3 - 2 * t);
        return from + ((present ? 1 : 0) - from) * t;
    }
}
