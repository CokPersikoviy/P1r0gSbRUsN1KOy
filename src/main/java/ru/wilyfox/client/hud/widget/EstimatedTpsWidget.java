package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.performance.EstimatedTpsMonitor;

public class EstimatedTpsWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int EMPTY_WIDTH = 150;
    private static final int EMPTY_HEIGHT = 44;

    public EstimatedTpsWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        EstimatedTpsMonitor.Snapshot snapshot = EstimatedTpsMonitor.getSnapshot();
        if (!snapshot.enabled()) {
            renderPlaceholder(context, mc, "Monitor disabled");
            return;
        }

        if (!snapshot.available()) {
            renderPlaceholder(context, mc, snapshot.sampleCount() > 0 ? "Waiting for fresh packets" : "Waiting for samples");
            return;
        }

        String title = "Estimated TPS";
        String currentLine = "TPS: " + formatMetric(snapshot.currentTps());
        String onePercentLine = "1% low: " + formatMetric(snapshot.onePercentLow());
        String pointOnePercentLine = "0.1% low: " + formatMetric(snapshot.pointOnePercentLow());
        int width = getUnscaledWidth(mc, title, currentLine, onePercentLine, pointOnePercentLine);
        int height = getUnscaledHeight(mc);

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPanel(context, width, height);
        int lineY = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.drawString(mc.font, title, PADDING_X, lineY, WidgetTheme.TITLE);
            lineY += mc.font.lineHeight + 2;
        }
        context.drawString(mc.font, currentLine, PADDING_X, lineY, getMetricColor(snapshot.currentTps()));
        lineY += mc.font.lineHeight + 2;
        context.drawString(mc.font, onePercentLine, PADDING_X, lineY, getMetricColor(snapshot.onePercentLow()));
        lineY += mc.font.lineHeight + 2;
        context.drawString(mc.font, pointOnePercentLine, PADDING_X, lineY, getMetricColor(snapshot.pointOnePercentLow()));

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        EstimatedTpsMonitor.Snapshot snapshot = EstimatedTpsMonitor.getSnapshot();
        if (!snapshot.enabled() || !snapshot.available()) {
            return Math.round(EMPTY_WIDTH * getScale());
        }

        Minecraft mc = Minecraft.getInstance();
        String title = "Estimated TPS";
        String currentLine = "TPS: " + formatMetric(snapshot.currentTps());
        String onePercentLine = "1% low: " + formatMetric(snapshot.onePercentLow());
        String pointOnePercentLine = "0.1% low: " + formatMetric(snapshot.pointOnePercentLow());
        return Math.round(getUnscaledWidth(mc, title, currentLine, onePercentLine, pointOnePercentLine) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight(Minecraft.getInstance()) * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().estimatedTps.active || isEditorPreview();
    }

    @Override
    public String getDisplayName() {
        return "Estimated TPS";
    }

    private int getUnscaledWidth(Minecraft mc, String... lines) {
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, mc.font.width(line));
        }
        return Math.max(EMPTY_WIDTH, maxWidth + PADDING_X * 2);
    }

    private int getUnscaledHeight(Minecraft mc) {
        int lines = WidgetUtils.showWidgetTitles() ? 4 : 3;
        return Math.max(EMPTY_HEIGHT, PADDING_Y * 2 + mc.font.lineHeight * lines + 6);
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc, String subtitle) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.drawString(mc.font, "Estimated TPS", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, subtitle, PADDING_X, 18, WidgetTheme.TEXT_MUTED);
        context.drawString(mc.font, "Packet timing heuristic", PADDING_X, 29, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private String formatMetric(Double value) {
        if (value == null) {
            return "--";
        }
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private int getMetricColor(Double value) {
        if (value == null) {
            return WidgetTheme.TEXT_MUTED;
        }
        if (value >= 18.0D) {
            return WidgetTheme.STATUS_SUCCESS;
        }
        if (value >= 15.0D) {
            return WidgetTheme.STATUS_WARNING;
        }
        return WidgetTheme.STATUS_ERROR;
    }
}

