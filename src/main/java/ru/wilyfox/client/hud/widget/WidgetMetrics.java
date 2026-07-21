package ru.wilyfox.client.hud.widget;

/**
 * Shared spacing/geometry rhythm for the HUD widget system, in unscaled game pixels. Replaces the
 * ad-hoc PADDING_X / LINE_GAP constants scattered per widget so the whole HUD shares one rhythm.
 */
public final class WidgetMetrics {
    public static final int PAD_X = 6;
    public static final int PAD_Y = 5;
    public static final int LINE_GAP = 1;
    public static final int ROW_GAP = 3;
    public static final int TITLE_GAP = 2;
    public static final int BAR_HEIGHT = 3;

    /** Custom-renderer rounded corner radius. Clamped to min(w,h)/2 per draw, so never a pill. */
    public static final int RADIUS = 5;
    /** Native-renderer pixel chamfer (clipped corner). */
    public static final int CHAMFER = 2;

    public static final int ICON = 16;

    private WidgetMetrics() {
    }
}
