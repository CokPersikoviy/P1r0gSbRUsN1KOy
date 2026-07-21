package ru.wilyfox.client.hud.widget;

import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.profiler.ModProfiler;

/**
 * The single surface primitive every widget draws through — "Frost" design language.
 *
 * <p>Two orthogonal axes:
 * <ul>
 *   <li><b>Chrome</b> ({@link WidgetChrome}): BARE / SOLID / FROST — how much surface.</li>
 *   <li><b>Renderer</b>: custom (rounded corners + real frosted blur via {@link HudBlur}) or native
 *       (flat tint + pixel chamfers, no blur) — toggled by <code>render.nativeRenderer</code> for
 *       players whose GPU chokes on the blur.</li>
 * </ul>
 *
 * All geometry is plain {@link GuiGraphics#fill}; the frosted look is the blurred backdrop composited
 * by {@link HudBlur} under a light tint. Colour is inherited from the approved Frost spec.
 */
public final class HudSurface {
    // ---- Frost surface palette (approved "Frost" spec; cool tint, not the theme grey) ----
    /** Cool glass hue (rgb 20,24,32). Its alpha is the theme's BG Opacity slider (0..50%). */
    private static final int FROST_RGB = 0x141820;
    /** Solid chrome: heavier cool tint for very bright scenes. rgba(10,12,16,0.86). */
    private static final int SOLID_TINT = 0xDB0A0C10;
    /** Native renderer: flat, near-opaque cool tint (no blur). rgba(13,15,20,0.82). */
    private static final int NATIVE_TINT = 0xD10D0F14;

    private static final int FILL_LIT = 0x30FFFFFF;   // lit top of a progress fill

    private HudSurface() {
    }

    public static WidgetChrome chrome() {
        WidgetChrome chrome = ConfigManager.get().render.widgetChrome;
        return chrome == null ? WidgetChrome.FROST : chrome;
    }

    public static boolean nativeRenderer() {
        return ConfigManager.get().render.nativeRenderer;
    }

    /** Draw a widget background at (0,0,w,h) in the current (translated + scaled) pose. */
    public static void drawPanel(GuiGraphics context, int width, int height) {
        drawPanel(context, 0, 0, width, height, chrome(), nativeRenderer());
    }

    public static void drawPanel(GuiGraphics context, int x, int y, int w, int h, WidgetChrome chrome, boolean useNative) {
        if (chrome == WidgetChrome.BARE || w <= 0 || h <= 0) {
            return;
        }

        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("hud/surface/drawPanel")) {
            // The top accent line uses the theme's extrapolated accent colour (per preset / Hard Accent),
            // at its own fixed strength — independent of the BG Opacity slider (which is glass-only).
            int accent = WidgetTheme.ACCENT_LINE;
            if (useNative) {
                fillChamfered(context, x, y, w, h, WidgetMetrics.CHAMFER, panelTint(chrome, true));
                context.fill(x + WidgetMetrics.CHAMFER, y, x + w - WidgetMetrics.CHAMFER, y + 1, accent);
                return;
            }

            // Frosted glass: composite the blurred backdrop first, then pick the tint from whether the
            // blur actually landed this frame (light tint over live blur, heavier flat tint otherwise).
            if (chrome == WidgetChrome.FROST) {
                HudBlur.blurBehind(context, x, y, w, h, WidgetMetrics.RADIUS);
            }
            fillRounded(context, x, y, w, h, WidgetMetrics.RADIUS, panelTint(chrome, false));
            // One thin lit line along the top — the theme accent colour, not a hardcoded white.
            context.fill(x + WidgetMetrics.RADIUS, y, x + w - WidgetMetrics.RADIUS, y + 1, accent);
        }
    }

    /**
     * Editor-preview surface for an empty widget. Reflects the current chrome so switching
     * Background is visible even without live data. BARE has no in-game panel, so here it draws a
     * faint outline to keep the empty widget visible and grabbable in the editor.
     */
    public static void drawPlaceholderPanel(GuiGraphics context, int w, int h) {
        WidgetChrome chrome = chrome();
        if (chrome == WidgetChrome.BARE) {
            int corner = nativeRenderer() ? WidgetMetrics.CHAMFER : WidgetMetrics.RADIUS;
            strokeEdges(context, 0, 0, w, h, corner, 0x40FFFFFF, 0x40FFFFFF);
            return;
        }
        drawPanel(context, 0, 0, w, h, chrome, nativeRenderer());
    }

    /** Progress bar in the surface language: rounded track + lit fill (or flat, native). */
    public static void drawBar(GuiGraphics context, int x, int y, int w, int h, float progress, int fillColor) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("hud/surface/drawBar")) {
            drawBarInner(context, x, y, w, h, progress, fillColor);
        }
    }

    private static void drawBarInner(GuiGraphics context, int x, int y, int w, int h, float progress, int fillColor) {
        boolean useNative = nativeRenderer();
        int corner = Math.min(h / 2, 2);
        if (useNative || corner <= 0) {
            context.fill(x, y, x + w, y + h, WidgetTheme.BAR_BG);
        } else {
            fillRounded(context, x, y, w, h, corner, WidgetTheme.BAR_BG);
        }

        int fillW = Math.max(0, Math.min(w, Math.round(w * clamp01(progress))));
        if (fillW <= 0) {
            return;
        }

        if (useNative || corner <= 0) {
            context.fill(x, y, x + fillW, y + h, fillColor);
        } else {
            fillRounded(context, x, y, fillW, h, corner, fillColor);
        }
        context.fill(x, y, x + fillW, y + 1, FILL_LIT);
    }

    // ---- geometry helpers (pure GuiGraphics.fill) ----

    /** Sub-pixel oversampling factor for corners: the arc is drawn on a grid this much finer than a
     *  GUI pixel, so the curve resolves onto physical pixels instead of chunky GUI-pixel steps. */
    private static final int CORNER_SS = 4;

    /**
     * Anti-aliased rounded-rect fill. Straight regions are solid fills; each corner is drawn through a
     * {@code 1/CORNER_SS} down-scaled pose, so its quarter-circle is rasterised at sub-GUI-pixel
     * resolution (scanline spans + a fractional-coverage edge sliver). That is what makes the curve
     * smooth — a GUI pixel is huge at high GUI scale, so 5 of them can never be smooth. No sprite, no shader.
     */
    public static void fillRounded(GuiGraphics context, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            context.fill(x, y, x + w, y + h, color);
            return;
        }

        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("hud/surface/fillRounded")) {
            ModProfiler.getInstance().incrementCounter("hud/surface/fillRounded/corners", 4);
            // Straight regions (top band, full-width middle, bottom band) — corners filled separately.
            context.fill(x + r, y, x + w - r, y + r, color);
            context.fill(x, y + r, x + w, y + h - r, color);
            context.fill(x + r, y + h - r, x + w - r, y + h, color);

            aaCorner(context, x, y, true, true, r, color);                 // top-left
            aaCorner(context, x + w - r, y, false, true, r, color);         // top-right
            aaCorner(context, x, y + h - r, true, false, r, color);         // bottom-left
            aaCorner(context, x + w - r, y + h - r, false, false, r, color); // bottom-right
        }
    }

    /**
     * Fill one rounded corner. (boxX, boxY) is the corner box's top-left in GUI coords. The centre of
     * curvature sits at whichever box corner is toward the panel interior — {@code centreRight}/
     * {@code centreBottom} pick it. The pose is scaled to {@code 1/CORNER_SS} (always positive), so the
     * quarter circle is a scanline fill on a grid {@code CORNER_SS}× finer than a GUI pixel, with the
     * boundary sub-pixel alpha-weighted by coverage. That sub-GUI-pixel resolution is what makes it smooth.
     */
    private static void aaCorner(GuiGraphics context, int boxX, int boxY, boolean centreRight, boolean centreBottom, int r, int color) {
        int baseAlpha = color >>> 24;
        int rgb = color & 0x00FFFFFF;
        if (baseAlpha == 0) {
            return;
        }
        int radius = r * CORNER_SS;
        int cx = centreRight ? radius : 0;
        int cy = centreBottom ? radius : 0;
        int quads = 0;

        context.pose().pushPose();
        context.pose().translate(boxX, boxY, 0);
        context.pose().scale(1.0f / CORNER_SS, 1.0f / CORNER_SS, 1.0f);
        for (int py = 0; py < radius; py++) {
            double dy = (py + 0.5) - cy;
            double half = Math.sqrt(Math.max(0.0, (double) radius * radius - dy * dy));
            if (centreRight) {
                // interior is to the right: curve boundary on the left at x = radius - half
                double edge = radius - half;
                int solid = (int) Math.ceil(edge);
                if (solid < radius) {
                    context.fill(solid, py, radius, py + 1, color);
                    quads++;
                }
                if (solid - 1 >= 0) {
                    int a = (int) Math.round(baseAlpha * (solid - edge));
                    if (a > 0) {
                        context.fill(solid - 1, py, solid, py + 1, (a << 24) | rgb);
                        quads++;
                    }
                }
            } else {
                // interior is to the left: curve boundary on the right at x = half
                int solid = (int) Math.floor(half);
                if (solid > 0) {
                    context.fill(0, py, solid, py + 1, color);
                    quads++;
                }
                if (solid < radius) {
                    int a = (int) Math.round(baseAlpha * (half - solid));
                    if (a > 0) {
                        context.fill(solid, py, solid + 1, py + 1, (a << 24) | rgb);
                        quads++;
                    }
                }
            }
        }
        context.pose().popPose();
        ModProfiler.getInstance().incrementCounter("hud/surface/fillRounded/aaQuads", quads);
    }

    /** 1px edge lines along the four sides (corners left open — invisible at this radius). */
    private static void strokeEdges(GuiGraphics context, int x, int y, int w, int h, int r, int topColor, int sideColor) {
        context.fill(x + r, y, x + w - r, y + 1, topColor);
        context.fill(x + r, y + h - 1, x + w - r, y + h, sideColor);
        context.fill(x, y + r, x + 1, y + h - r, sideColor);
        context.fill(x + w - 1, y + r, x + w, y + h - r, sideColor);
    }

    /** Flat fill with pixel-chamfered (clipped) corners — the native, GuiGraphics-only surface. */
    public static void fillChamfered(GuiGraphics context, int x, int y, int w, int h, int c, int color) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("hud/surface/fillChamfered")) {
            c = Math.max(0, Math.min(c, Math.min(w, h) / 2));
            for (int i = 0; i < c; i++) {
                int inset = c - i;
                context.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
                context.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
            }
            context.fill(x, y + c, x + w, y + h - c, color);
        }
    }

    private static int panelTint(WidgetChrome chrome, boolean useNative) {
        if (chrome == WidgetChrome.SOLID) {
            return SOLID_TINT;
        }
        // FROST + native fallback (no blur) stays a heavier flat tint so text reads without the blur.
        if (useNative) {
            return NATIVE_TINT;
        }
        // FROST: cool glass over the live blur; opacity is the theme's BG Opacity slider (0..50%).
        // For a heavier surface the Solid mode exists separately, so the slider is capped low.
        int pct = Math.max(0, Math.min(50, ConfigManager.get().theme.widgetBackgroundOpacityPercent));
        int alpha = Math.round(pct / 100.0f * 255.0f);
        return (alpha << 24) | FROST_RGB;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
