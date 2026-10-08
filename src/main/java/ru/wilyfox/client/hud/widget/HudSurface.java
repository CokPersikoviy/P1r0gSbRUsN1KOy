package ru.wilyfox.client.hud.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * Smooth panels submit their geometry as one {@link RoundedPanelRenderState}; the blurred backdrop is composited
 * by {@link HudBlur} under a tint derived from the active theme.
 */
public final class HudSurface {
    private HudSurface() {
    }

    public static WidgetChrome chrome() {
        WidgetChrome chrome = ConfigManager.get().render.widgetChrome;
        return chrome == null ? WidgetChrome.FROST : chrome;
    }

    public static boolean nativeRenderer() {
        return ConfigManager.get().render.nativeRenderer || ConfigManager.get().render.lightweightHud;
    }

    static boolean shouldUseSmoothGeometry(WidgetChrome chrome, boolean useNative) {
        return chrome != WidgetChrome.BARE && !useNative;
    }

    static boolean shouldUseBlur(WidgetChrome chrome, boolean useNative) {
        return chrome == WidgetChrome.FROST && !useNative;
    }

    /** Draw a widget background at (0,0,w,h) in the current (translated + scaled) pose. */
    public static void drawPanel(GuiGraphicsExtractor context, int width, int height) {
        drawPanel(context, 0, 0, width, height, chrome(), nativeRenderer());
    }

    public static void drawPanel(GuiGraphicsExtractor context, int x, int y, int w, int h, WidgetChrome chrome, boolean useNative) {
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
            fillRoundedSmooth(context, x, y, w, h, WidgetMetrics.RADIUS, panelTint(chrome, false));
            // One thin lit line along the top — the theme accent colour, not a hardcoded white.
            context.fill(x + WidgetMetrics.RADIUS, y, x + w - WidgetMetrics.RADIUS, y + 1, accent);
        }
    }

    /**
     * Editor-preview surface for an empty widget. Reflects the current chrome so switching
     * Background is visible even without live data. BARE has no in-game panel, so here it draws a
     * faint outline to keep the empty widget visible and grabbable in the editor.
     */
    public static void drawPlaceholderPanel(GuiGraphicsExtractor context, int w, int h) {
        WidgetChrome chrome = chrome();
        if (chrome == WidgetChrome.BARE) {
            int corner = nativeRenderer() ? WidgetMetrics.CHAMFER : WidgetMetrics.RADIUS;
            int outline = WidgetTheme.withAlpha(WidgetTheme.OUTLINE_SOFT, 0x40);
            strokeEdges(context, 0, 0, w, h, corner, outline, outline);
            return;
        }
        drawPanel(context, 0, 0, w, h, chrome, nativeRenderer());
    }

    /** Progress bar in the surface language: rounded track + lit fill (or flat, native). */
    public static void drawBar(GuiGraphicsExtractor context, int x, int y, int w, int h, float progress, int fillColor) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("hud/surface/drawBar")) {
            drawBarInner(context, x, y, w, h, progress, fillColor);
        }
    }

    private static void drawBarInner(GuiGraphicsExtractor context, int x, int y, int w, int h, float progress, int fillColor) {
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
        context.fill(x, y, x + fillW, y + 1, WidgetTheme.withAlpha(WidgetTheme.TEXT_SOFT, 0x30));
    }

    // ---- geometry helpers ----

    /** Draw the original 4x anti-aliased geometry as one GUI element, keeping text above it. */
    public static void fillRounded(GuiGraphicsExtractor context, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (!shouldUseSmoothGeometry(chrome(), nativeRenderer())) {
            context.fill(x, y, x + w, y + h, color);
            return;
        }

        fillRoundedSmooth(context, x, y, w, h, r, color);
    }

    private static void fillRoundedSmooth(GuiGraphicsExtractor context, int x, int y, int w, int h, int r, int color) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("hud/surface/fillRounded")) {
            RoundedPanelRenderState.fill(context, x, y, w, h, r, color);
            if ((color >>> 24) != 0) {
                ModProfiler.getInstance().incrementCounter("hud/surface/fillRounded/renderStates");
            }
        }
    }

    /** 1px edge lines along the four sides (corners left open — invisible at this radius). */
    private static void strokeEdges(GuiGraphicsExtractor context, int x, int y, int w, int h, int r, int topColor, int sideColor) {
        context.fill(x + r, y, x + w - r, y + 1, topColor);
        context.fill(x + r, y + h - 1, x + w - r, y + h, sideColor);
        context.fill(x, y + r, x + 1, y + h - r, sideColor);
        context.fill(x + w - 1, y + r, x + w, y + h - r, sideColor);
    }

    /** Flat fill with pixel-chamfered (clipped) corners — the native, GuiGraphicsExtractor-only surface. */
    public static void fillChamfered(GuiGraphicsExtractor context, int x, int y, int w, int h, int c, int color) {
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
            return WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0xDB);
        }
        if (useNative) {
            return WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0xD1);
        }
        int pct = Math.max(0, Math.min(50, ConfigManager.get().theme.widgetBackgroundOpacityPercent));
        int alpha = Math.round(pct / 100.0f * 255.0f);
        return WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, alpha);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
