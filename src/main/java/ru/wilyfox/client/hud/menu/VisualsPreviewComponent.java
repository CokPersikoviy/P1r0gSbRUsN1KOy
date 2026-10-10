package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Items;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.VisualsConfig;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.visuals.VisualCrosshair;
import ru.wilyfox.client.visuals.VisualLinePieces;
import ru.wilyfox.client.visuals.Visuals;

/** Lightweight illustrations; no framebuffer copies or world/entity scans for menu previews. */
final class VisualsPreviewComponent extends SettingsComponent {
    enum Kind { CROSSHAIR, HITBOXES, BLOCK_OUTLINE, HAND, CAMERA, WORLD, SCREEN, TAB }
    private final Kind kind;
    private int scenario;
    VisualsPreviewComponent(Kind kind) { super(0, 0, 0, 112, "Preview"); this.kind = kind; preferredHeight = 112; }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (!isHovered(mx, my) || button != 0) return false;
        scenario = (scenario + 1) % 6; return true;
    }
    @Override public void render(GuiGraphicsExtractor g, int mx, int my) {
        var c = ConfigManager.get().visuals; var font = Minecraft.getInstance().font;
        HudSurface.fillRounded(g, x, y, width, height, 4, WidgetTheme.PANEL_BG_SOFT);
        g.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        try {
            background(g, c);
            g.nextStratum();
            switch (kind) {
                case CROSSHAIR -> {
                    var h = c.crosshair;
                    double spread = switch (scenario) { case 1 -> h.runSpread; case 2 -> h.jumpSpread; case 3 -> h.attackSpread; default -> 0; };
                    double draw = scenario == 4 ? (System.nanoTime() / 1_000_000 % 3000) / 2000.0 : -1;
                    VisualCrosshair.preview(g, x + width / 2, y + 54, 1.5, spread, Math.min(1, draw), scenario == 5);
                }
                case HITBOXES -> {
                    var h = c.hitboxes;
                    int color = scenario == 5 && h.hover ? h.hoverColor : scenario == 3 && h.hit ? h.hitColor : h.color;
                    cube(g, x + width / 2 - 18, y + 79, 34, 57, h.style, color, h.width, h.fill ? h.fillColor : 0);
                    if (h.eyeLine) line(g, x + width / 2 - 18, y + 30, x + width / 2 + 16, y + 30, VisualsConfig.LineStyle.NORMAL, h.eyeColor, h.width);
                    if (h.lookArrow) { line(g, x + width / 2, y + 30, x + width / 2 + 65, y + 22, VisualsConfig.LineStyle.NORMAL, h.lookColor, h.width); }
                }
                case BLOCK_OUTLINE -> {
                    double drift = c.blockAnimation > 0 && scenario == 1 ? Math.sin(System.nanoTime() / 1e9) * 16 : 0;
                    int color = c.blockRainbow ? c.blockOutlineColor & 0xFF000000 | net.minecraft.util.Mth.hsvToRgb((System.nanoTime() / 1_000_000 % 2400) / 2400f, .65f, 1) : c.blockOutlineColor;
                    cube(g, x + width / 2 - 25 + drift, y + 76, 50, c.blockMode == VisualsConfig.OutlineMode.EXACT ? 25 : 45, c.blockStyle, color, c.blockOutlineWidth, c.blockFill ? c.blockFillColor : 0);
                }
                case HAND -> {
                    hand(g, x + width / 3, y + 55, c.hand.main, 1);
                    hand(g, x + width * 2 / 3, y + 55, c.hand.mirror ? c.hand.main : c.hand.off, -1);
                    g.centeredText(font, "Main", x + width / 3, y + 88, WidgetTheme.TEXT_SOFT);
                    g.centeredText(font, "Off", x + width * 2 / 3, y + 88, WidgetTheme.TEXT_SOFT);
                }
                case CAMERA -> {
                    cube(g, x + width / 2 - 8, y + 69, 16, 35, VisualsConfig.LineStyle.NORMAL, WidgetTheme.TEXT_PRIMARY, 1.5, 0);
                    double distance = c.camera.enabled ? c.camera.distance : c.camera.thirdPersonDistance;
                    double angle = c.camera.frontView ? Math.PI : 0;
                    if (scenario == 1) angle += System.nanoTime() / 1e9;
                    double px = x + width / 2 + Math.cos(angle) * Math.min(width * .35, distance * 10), py = y + 52 + Math.sin(angle) * 20;
                    line(g, x + width / 2, y + 52, px, py, VisualsConfig.LineStyle.DASHED, WidgetTheme.ACCENT_LINE, 1);
                    g.fill((int)px - 4, (int)py - 3, (int)px + 4, (int)py + 3, WidgetTheme.TITLE);
                }
                case SCREEN -> {
                    var window = Minecraft.getInstance().getWindow();
                    double ratio = c.ratio() > 0 ? c.ratio() : window.getWidth() / (double)Math.max(1, window.getHeight());
                    int h = 54, w = Math.min(width - 24, (int)(h * ratio)), px = x + (width - w) / 2, py = y + 28;
                    g.fill(px - 2, py - 2, px + w + 2, py + h + 2, WidgetTheme.ACCENT_LINE);
                    g.fill(px, py, px + w, py + h, WidgetTheme.PANEL_BG);
                    g.centeredText(font, c.aspectRatio.title, x + width / 2, py + 23, WidgetTheme.TITLE);
                }
                case TAB -> {
                    int columns = Math.min(c.tab.columns, 4), cell = Math.max(1, (width - 20) / columns);
                    for (int col = 0; col < columns; col++) for (int row = 0; row < Math.min(c.tab.rows, 4); row++) {
                        int px = x + 10 + col * cell, py = y + 26 + row * 14;
                        g.fill(px, py, px + cell - 3, py + 12, col == 0 && row == 0 && scenario == 5 ? c.tab.highlight : WidgetTheme.PANEL_BG);
                        g.text(font, font.plainSubstrByWidth("Player" + (col * c.tab.rows + row + 1), cell - 8), px + 3, py + 2, WidgetTheme.TEXT_PRIMARY, false);
                    }
                }
                case WORLD -> {
                    if (c.weather == VisualsConfig.Weather.RAIN || c.weather == VisualsConfig.Weather.THUNDER)
                        for (int i = 0; i < width / 14; i++) line(g, x + 7 + i * 14, y + 25, x + i * 14, y + 75, VisualsConfig.LineStyle.NORMAL, 0x70557CAA, 1);
                    int light = c.blockLightEnabled ? c.blockLightColor : 0xFFFFD8A0;
                    cube(g, x + width / 2 - 25, y + 81, 48, 36, VisualsConfig.LineStyle.NORMAL, light, 1, Visuals.alpha(light, .6));
                }
            }
            g.nextStratum();
            String note = switch (kind) {
                case CROSSHAIR, HITBOXES -> new String[]{"Idle", "Run", "Jump", "Attack", "Draw", "Target"}[scenario];
                case HAND -> "Offsets / scale";
                case BLOCK_OUTLINE -> "Shape / line style";
                default -> "Preview";
            };
            g.text(font, note + " - click to change scene", x + 7, y + 6, WidgetTheme.TEXT_PRIMARY, false);
        } finally { g.disableScissor(); }
    }
    private void background(GuiGraphicsExtractor g, VisualsConfig c) {
        int sky = c.skyColorEnabled ? c.skyColor | 0xFF000000 : 0xFF8DB4DE;
        if (c.timeEnabled && c.time > 13000 && c.time < 23000) sky = Visuals.mix(sky, 0xFF101831, .85);
        g.fill(x + 1, y + 1, x + width - 1, y + 73, sky);
        int ground = c.skyLightEnabled ? Visuals.mix(0xFF56753D, c.skyLightColor | 0xFF000000, .35) : 0xFF56753D;
        g.fill(x + 1, y + 73, x + width - 1, y + height - 1, ground);
        int cloud = c.cloudColorEnabled ? c.cloudColor : 0xB0FFFFFF;
        g.fill(x + width - 80, y + 29, x + width - 14, y + 34, cloud);
        if (c.fogMode != VisualsConfig.FogMode.NONE) {
            int fog = c.fogColorEnabled ? c.fogColor : sky;
            double opacity = c.fogMode == VisualsConfig.FogMode.CUSTOM ? .18 / c.fogDistance : .18;
            g.fill(x + 1, y + 62, x + width - 1, y + 83, Visuals.alpha(fog | 0xFF000000, opacity));
        }
    }
    private static void hand(GuiGraphicsExtractor g, int x, int y, VisualsConfig.Transform t, int sign) {
        g.pose().pushMatrix();
        try {
            g.pose().translate((float)(x + t.x * sign * 20), (float)(y - t.y * 20));
            g.pose().rotate((float)Math.toRadians(t.roll * sign)); g.pose().scale((float)(2 * t.scale));
            g.item(Items.DIAMOND_SWORD.getDefaultInstance(), -8, -8);
        } finally { g.pose().popMatrix(); }
    }
    private static void cube(GuiGraphicsExtractor g, double x, double y, double w, double h, VisualsConfig.LineStyle style, int color, double thickness, int fill) {
        if (fill >>> 24 != 0) g.fill((int)x, (int)(y - h), (int)(x + w), (int)y, fill);
        double skew = w * .35, lift = w * .23;
        double[][] points = {{x,y},{x+w,y},{x+w,y-h},{x,y-h},{x+skew,y-lift},{x+w+skew,y-lift},{x+w+skew,y-h-lift},{x+skew,y-h-lift}};
        int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        for (int[] e : edges) line(g, points[e[0]][0], points[e[0]][1], points[e[1]][0], points[e[1]][1], style, color, thickness);
    }
    private static void line(GuiGraphicsExtractor g, double x, double y, double a, double b, VisualsConfig.LineStyle style, int color, double width) {
        double dx = a - x, dy = b - y, length = Math.hypot(dx, dy);
        VisualLinePieces.forEach(style, length / 45, (from, to) -> {
            g.pose().pushMatrix();
            try {
                g.pose().translate((float)(x + dx * from), (float)(y + dy * from)); g.pose().rotate((float)Math.atan2(dy, dx));
                int end = Math.max(1, (int)Math.round(length * (to - from))), half = Math.max(1, (int)Math.round(width / 2));
                if (style == VisualsConfig.LineStyle.NEON) g.fill(0, -half - 2, end, half + 2, Visuals.alpha(color, .2));
                g.fill(0, -half, end, half, color);
            } finally { g.pose().popMatrix(); }
        });
    }
}
