package ru.wilyfox.client.hud.internal;

/**
 * A monotonic HUD frame counter, advanced once at the start of each HUD render pass. Widgets whose
 * size/content is derived from live data (e.g. the boss timer) use it as a cache key so an
 * expensive-to-build value is computed at most once per frame instead of once per caller
 * (isVisible + getWidth + getHeight + render all ask for it within the same frame).
 */
public final class HudFrameClock {
    private static long frame;

    private HudFrameClock() {
    }

    /** Advance to the next frame. Called once per HUD render pass. */
    public static void advance() {
        frame++;
    }

    /** The current frame id. Stable across all calls within a single HUD render pass. */
    public static long current() {
        return frame;
    }
}
