package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.performance.FpsMonitor;
import ru.wilyfox.client.profiler.ModProfiler;

public final class FpsWidget extends AbstractWidget {
    private static final int PAD_X = 6, PAD_Y = 5, GAP = 12;
    private final FpsMonitor monitor;
    private FpsMonitor.Snapshot snapshot;
    private final String[] labels = new String[3];
    private boolean horizontal, titled;

    public FpsWidget(int x, int y, HudLayer layer) { this(x, y, layer, FpsMonitor.INSTANCE); }
    public FpsWidget(int x, int y, HudLayer layer, FpsMonitor monitor) {
        super(x, y, layer); this.monitor = monitor;
    }
    private void refresh() {
        FpsMonitor.Snapshot next;
        try (var profile = ModProfiler.getInstance().scope("hud/FpsWidget/statistics")) {
            next = monitor.snapshot(System.nanoTime());
        }
        boolean row = ConfigManager.get().fpsWidget.horizontal, title = WidgetUtils.showWidgetTitles();
        if (next == snapshot && row == horizontal && title == titled) return;
        snapshot = next; horizontal = row; titled = title;
        labels[0] = "AVG: " + metric(next.average());
        labels[1] = "1% low: " + metric(next.onePercentLow());
        labels[2] = "0.1% low: " + metric(next.pointOnePercentLow());
        var font = Minecraft.getInstance().font;
        int contentWidth = horizontal ? GAP * 2 : 0;
        for (String label : labels) contentWidth = horizontal ? contentWidth + font.width(label) : Math.max(contentWidth, font.width(label));
        width = PAD_X * 2 + Math.max(titled ? font.width("FPS") : 0, contentWidth);
        height = PAD_Y * 2 + (font.lineHeight + 2) * ((horizontal ? 1 : 3) + (titled ? 1 : 0)) - 2;
    }
    @Override public int getWidth() { refresh(); return Math.round(width * scale); }
    @Override public int getHeight() { refresh(); return Math.round(height * scale); }
    @Override public boolean isVisible() { return isInLayout(); }
    @Override public String getDisplayName() { return "FPS"; }
    @Override public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (!isVisible()) return;
        refresh();
        var font = Minecraft.getInstance().font;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(startX, startY).scale(scale, scale);
            HudSurface.drawPanel(graphics, width, height);
            int x = PAD_X, y = PAD_Y;
            if (titled) { graphics.text(font, "FPS", x, y, WidgetTheme.TITLE); y += font.lineHeight + 2; }
            for (int i = 0; i < labels.length; i++) {
                graphics.text(font, labels[i], x, y, i == 0 ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SOFT);
                if (horizontal) x += font.width(labels[i]) + GAP;
                else y += font.lineHeight + 2;
            }
        } finally { graphics.pose().popMatrix(); }
    }
    private static String metric(double value) { return Double.isFinite(value) ? Long.toString(Math.round(value)) : "--"; }
}
