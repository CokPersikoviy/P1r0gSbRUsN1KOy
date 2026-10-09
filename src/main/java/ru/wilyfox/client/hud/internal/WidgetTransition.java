package ru.wilyfox.client.hud.internal;

/** Render-only animation. The widget model and saved layout always hold the destination. */
public final class WidgetTransition {
    public static final long FADE_NANOS = 500_000_000L;
    public static final long MOVE_NANOS = 200_000_000L;
    private boolean visible;
    private float fromAlpha, fromX, fromY, fromScale;
    private float x, y, scale;
    private long fadeStart, moveStart;

    public WidgetTransition(boolean visible, float x, float y, float scale) {
        this.visible = visible;
        fromAlpha = visible ? 1 : 0;
        this.x = fromX = x; this.y = fromY = y; this.scale = fromScale = scale;
        fadeStart = moveStart = Long.MIN_VALUE;
    }

    public void retarget(boolean visible, float x, float y, float scale, long now) {
        float currentX = x(now), currentY = y(now), currentScale = scale(now);
        if (visible != this.visible) {
            fromAlpha = alpha(now); fadeStart = now;
        }
        // A newly appearing widget starts at its destination, rather than sliding from a stale layout.
        if (!this.visible && alpha(now) == 0) {
            fromX = x; fromY = y; fromScale = scale; moveStart = Long.MIN_VALUE;
        } else if (visible && (this.x != x || this.y != y || this.scale != scale)) {
            fromX = currentX; fromY = currentY; fromScale = currentScale; moveStart = now;
        }
        this.visible = visible;
        if (visible) { this.x = x; this.y = y; this.scale = scale; }
        else { this.x = fromX = currentX; this.y = fromY = currentY; this.scale = fromScale = currentScale; moveStart = Long.MIN_VALUE; }
    }

    /** Follow passive anchors/size adjustments without restarting a location animation. */
    public void destination(float x, float y, float scale) {
        this.x = x; this.y = y; this.scale = scale;
    }
    public boolean visible() { return visible; }
    public float alpha(long now) { return lerp(fromAlpha, visible ? 1 : 0, fraction(now, fadeStart, FADE_NANOS)); }
    public float x(long now) { return lerp(fromX, x, smooth(fraction(now, moveStart, MOVE_NANOS))); }
    public float y(long now) { return lerp(fromY, y, smooth(fraction(now, moveStart, MOVE_NANOS))); }
    public float scale(long now) { return lerp(fromScale, scale, smooth(fraction(now, moveStart, MOVE_NANOS))); }
    private static float fraction(long now, long start, long duration) {
        return start == Long.MIN_VALUE ? 1 : Math.clamp((float) (now - start) / duration, 0, 1);
    }
    private static float smooth(float t) { return t * t * (3 - 2 * t); }
    private static float lerp(float from, float to, float t) { return from + (to - from) * t; }
}
