package ru.wilyfox.client.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.util.Util;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.mixin.VisualsLoadingOverlayAccessor;

/** Keeps vanilla reload completion/error callbacks and serialization of pending reloads. */
public final class CompactReload {
    private static LoadingOverlay detached;
    private static float progress;
    private static long finishedAt = -1;
    private CompactReload() {}
    public static boolean capture(Overlay overlay) {
        if (!ConfigManager.get().visuals.compactReload || Minecraft.getInstance().level == null || !(overlay instanceof LoadingOverlay loading)) return false;
        if (detached != null) return false;
        ((VisualsLoadingOverlayAccessor) loading).froghelper$fadeInStart(Util.getMillis() - 1000);
        detached = loading; progress = 0; finishedAt = -1; return true;
    }
    public static Overlay pending() { return detached; }
    public static void tick(Minecraft mc) {
        var loading = detached; if (loading == null) return;
        loading.tick();
        if (mc.level == null || !ConfigManager.get().visuals.compactReload) {
            detached = null;
            if (mc.gui.overlay() == null) mc.gui.setOverlay(loading);
            return;
        }
        long now = Util.getMillis();
        if (finishedAt < 0 && ((VisualsLoadingOverlayAccessor) loading).froghelper$fadeOutStart() >= 0) finishedAt = now;
        if (finishedAt >= 0 && now - finishedAt >= 500) detached = null;
    }
    public static void render(GuiGraphicsExtractor graphics) {
        if (detached == null) return;
        progress = finishedAt < 0 ? Math.clamp(progress * 0.95f + ((VisualsLoadingOverlayAccessor) detached).froghelper$reload().getActualProgress() * 0.05f, 0, 1) : 1;
        double fade = finishedAt < 0 ? 1 : 1 - Math.clamp((Util.getMillis() - finishedAt) / 500.0, 0, 1);
        int width = Math.min(240, graphics.guiWidth() - 24), x = (graphics.guiWidth() - width) / 2, y = graphics.guiHeight() - 28;
        if (width <= 0) return;
        graphics.nextStratum();
        graphics.fill(x, y, x + width, y + 6, Visuals.alpha(WidgetTheme.PANEL_BG, fade));
        graphics.fill(x + 1, y + 1, x + 1 + Math.round((width - 2) * progress), y + 5, Visuals.alpha(WidgetTheme.ACCENT_LINE, fade));
    }
}
