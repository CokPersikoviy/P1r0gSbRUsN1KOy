package ru.wilyfox.client.visuals;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.VisualsConfig;
import ru.wilyfox.client.profiler.ModProfiler;

public final class VisualCrosshair {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("froghelper", "textures/gui/visuals_white.png");
    private static final CrosshairGeometry GEOMETRY = new CrosshairGeometry();
    private static final CrosshairGeometry PREVIEW = new CrosshairGeometry();
    private static double spread, lastDraw = -1;
    private static long spreadAt, fullAt;
    private VisualCrosshair() {}
    public static void render(GuiGraphicsExtractor graphics) {
        var mc = Minecraft.getInstance(); var c = ConfigManager.get().visuals.crosshair;
        if (mc.player == null) return;
        try (var profile = ModProfiler.getInstance().scope("visuals/crosshair")) {
            long now = System.nanoTime(); double dt = Math.clamp((now - spreadAt) / 1e9, 0, 0.1); spreadAt = now;
            float tick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            double target = 0;
            if (c.dynamic) {
                var motion = mc.player.getDeltaMovement();
                target = Math.max(Math.clamp(Math.sqrt(motion.x * motion.x + motion.z * motion.z) / 0.28, 0, 1) * c.runSpread,
                        Math.max(!mc.player.onGround() ? c.jumpSpread : 0, mc.player.getAttackAnim(tick) > 0 ? c.attackSpread : 0));
            }
            spread = c.dynamic ? spread + (target - spread) * (1 - Math.exp(-dt * (target > spread ? 20 : c.recovery))) : 0;
            double draw = c.drawEnabled ? drawProgress(tick) : -1;
            if (draw >= 1 && lastDraw < 1) fullAt = now;
            lastDraw = draw;
            double flash = c.drawFlash && draw >= 1 ? Math.clamp(1 - (now - fullAt) / 260_000_000.0, 0, 1) : 0;
            double extra = spread + (c.drawEnabled && c.drawGap && draw >= 0 ? c.drawGapFrom + (c.drawGapTo - c.drawGapFrom) * draw : 0);
            var window = mc.getWindow(); double scale = c.scale == VisualsConfig.PixelScale.HEIGHT ? window.getHeight() / (double) c.referenceHeight : 1;
            int x = window.getWidth() / 2, y = window.getHeight() / 2;
            var entity = mc.crosshairPickEntity;
            boolean onTarget = entity instanceof LivingEntity living && living.isAlive() || entity instanceof Interaction;
            graphics.pose().pushMatrix();
            try {
                graphics.pose().scale(1f / Math.max(1, window.getGuiScale()));
                paint(graphics, x, y, scale, extra, draw, flash, onTarget, c, GEOMETRY);
            } finally { graphics.pose().popMatrix(); }
        }
    }
    public static void preview(GuiGraphicsExtractor graphics, int x, int y, double scale, double spread, double draw, boolean target) {
        var c = ConfigManager.get().visuals.crosshair;
        double extra = (c.dynamic ? spread : 0) + (c.drawEnabled && c.drawGap && draw >= 0 ? c.drawGapFrom + (c.drawGapTo - c.drawGapFrom) * draw : 0);
        paint(graphics, x, y, scale, extra, c.drawEnabled ? draw : -1, 0, target, c, PREVIEW);
    }
    private static void paint(GuiGraphicsExtractor graphics, int x, int y, double scale, double extra, double draw, double flash,
                              boolean onTarget, VisualsConfig.Crosshair c, CrosshairGeometry geometry) {
        int t = Math.max(1, px(c.thickness, scale));
        int dot = c.dot || c.style == VisualsConfig.CrosshairStyle.DOT ? Math.max(1, px(c.dotSize, scale)) : 0;
        if (dot > 0 && c.style != VisualsConfig.CrosshairStyle.DOT && (dot - t) % 2 != 0) dot++;
        var shape = geometry.shape(new CrosshairGeometry.Pixels(c.style, px(c.length, scale), px(c.splitLength ? c.verticalLength : c.length, scale),
                t, px(c.gap + extra, scale), c.top, c.bottom, c.left, c.right, dot, c.outline && !c.invert ? Math.max(1, px(c.outlineThickness, scale)) : 0));
        int color = tint(c.targetColorEnabled && onTarget ? c.targetColor : c.color, draw, flash, c);
        fills(graphics, x, y, shape.outline(), c.outlineColor, false);
        fills(graphics, x, y, shape.lines(), color, c.invert);
        fills(graphics, x, y, shape.dot(), c.dotColorEnabled ? tint(c.dotColor, draw, flash, c) : color, c.invert);
        if (c.drawEnabled && c.indicator != VisualsConfig.Indicator.OFF && draw >= 0) indicator(graphics, x, y, scale, draw, flash, shape, c);
    }
    private static int px(double value, double scale) { return Math.clamp((int) Math.round(value * scale), -256, 256); }
    public static double bowPower(double ticks) { double f = Math.max(0, ticks) / 20; return Math.min(1, (f * f + f * 2) / 3); }
    private static double drawProgress(float tick) {
        var player = Minecraft.getInstance().player;
        if (!player.isUsingItem()) return -1;
        var item = player.getUseItem(); double ticks = player.getTicksUsingItem(tick);
        if (item.getItem() instanceof BowItem) return bowPower(ticks);
        if (item.getItem() instanceof CrossbowItem) return CrossbowItem.isCharged(item) ? -1 : Math.clamp(ticks / Math.max(1, CrossbowItem.getChargeDuration(item, player)), 0, 1);
        if (item.getItem() instanceof TridentItem) return Math.clamp(ticks / 10, 0, 1);
        return -1;
    }
    private static int tint(int color, double draw, double flash, VisualsConfig.Crosshair c) {
        if (draw < 0) return color;
        color = switch (c.drawColor) { case OFF -> color; case FULL -> draw >= 1 ? c.fullColor : color; case SMOOTH -> Visuals.mix(color, c.fullColor, draw); };
        return flash > 0 ? Visuals.mix(color, color | 0xFFFFFF, flash * 0.8) : color;
    }
    private static void fills(GuiGraphicsExtractor graphics, int x, int y, List<CrosshairGeometry.Rect> rects, int color, boolean inverted) {
        for (var r : rects) {
            if (inverted) graphics.blit(RenderPipelines.CROSSHAIR, WHITE, x + r.x(), y + r.y(), 0f, 0f, r.width(), r.height(), r.width(), r.height(), -1);
            else graphics.fill(x + r.x(), y + r.y(), x + r.x() + r.width(), y + r.y() + r.height(), color);
        }
    }
    private static void indicator(GuiGraphicsExtractor graphics, int x, int y, double scale, double progress, double flash, CrosshairGeometry.Shape shape, VisualsConfig.Crosshair c) {
        int size = Math.clamp(px(c.indicatorSize, scale), 1, 128), t = Math.max(1, px(c.indicatorThickness, scale));
        var fill = new ArrayList<CrosshairGeometry.Rect>(); var track = new ArrayList<CrosshairGeometry.Rect>();
        if (c.indicator == VisualsConfig.Indicator.BAR) {
            int width = size * 2; if ((width - shape.parity()) % 2 != 0) width++;
            int start = (shape.parity() - width) / 2, py = shape.bottom() + Math.max(2, t), filled = (int) Math.round(width * progress);
            if (filled > 0) fill.add(new CrosshairGeometry.Rect(start, py, filled, t));
            if (filled < width) track.add(new CrosshairGeometry.Rect(start + filled, py, width - filled, t));
        } else {
            var ring = new ArrayList<CrosshairGeometry.Rect>(); double center = shape.parity() / 2.0;
            CrosshairGeometry.ring(ring, center, size, size + t);
            for (var row : ring) {
                int run = row.x(); boolean last = filled(run, row.y(), center, progress);
                for (int px = run + 1; px <= row.x() + row.width(); px++) {
                    boolean next = px < row.x() + row.width() && filled(px, row.y(), center, progress);
                    if (px == row.x() + row.width() || next != last) {
                        (last ? fill : track).add(new CrosshairGeometry.Rect(run, row.y(), px - run, 1)); run = px; last = next;
                    }
                }
            }
        }
        if (c.outline) {
            var all = new ArrayList<>(fill); all.addAll(track); var expanded = new ArrayList<CrosshairGeometry.Rect>();
            int o = Math.max(1, px(c.outlineThickness, scale));
            for (var r : all) expanded.add(new CrosshairGeometry.Rect(r.x() - o, r.y() - o, r.width() + 2 * o, r.height() + 2 * o));
            fills(graphics, x, y, CrosshairGeometry.region(expanded, all), c.outlineColor, false);
        }
        fills(graphics, x, y, track, Visuals.alpha(c.indicatorColor, 0.3), false);
        fills(graphics, x, y, fill, tint(c.indicatorColor, progress, flash, c), false);
    }
    private static boolean filled(int x, int y, double center, double progress) {
        double angle = Math.atan2(x + 0.5 - center, -(y + 0.5 - center)); if (angle < 0) angle += Math.PI * 2;
        return angle < progress * Math.PI * 2;
    }
}
