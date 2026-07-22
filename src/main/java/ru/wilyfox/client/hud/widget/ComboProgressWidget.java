package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.combo.ComboProgressStore;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ComboProgressWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int BAR_HEIGHT = 3;
    private static final int EMPTY_WIDTH = 118;
    private static final int EMPTY_HEIGHT = 26;
    private static final DecimalFormat MULTIPLIER_FORMAT = new DecimalFormat("0.0#", DecimalFormatSymbols.getInstance(Locale.US));

    private final ComboProgressStore store;

    public ComboProgressWidget(int x, int y, HudLayer layer, ComboProgressStore store) {
        super(x, y, layer);
        this.store = store;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ComboProgressStore.Snapshot snapshot = store.getSnapshot();
        if (!snapshot.available()) {
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context, mc);
            return;
        }

        long now = System.currentTimeMillis();
        List<RenderLine> lines = getLines(snapshot, now);
        boolean showBar = shouldShowBar(snapshot);
        int width = getUnscaledWidth(mc, lines);
        int height = getUnscaledHeight(mc, lines.size(), showBar);

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPanel(context, width, height);
        int lineY = PADDING_Y;
        for (RenderLine line : lines) {
            context.drawString(mc.font, line.text(), PADDING_X, lineY, line.color());
            lineY += mc.font.lineHeight + 2;
        }

        if (showBar) {
            int barY = height - PADDING_Y - BAR_HEIGHT;
            HudSurface.drawBar(context, PADDING_X, barY, width - PADDING_X * 2, BAR_HEIGHT,
                    (float) snapshot.progress(), WidgetTheme.BAR_FILL);
        }

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        ComboProgressStore.Snapshot snapshot = store.getSnapshot();
        if (!snapshot.available()) {
            return Math.round(EMPTY_WIDTH * getScale());
        }

        Minecraft mc = Minecraft.getInstance();
        List<RenderLine> lines = getLines(snapshot, System.currentTimeMillis());
        return Math.round(getUnscaledWidth(mc, lines) * getScale());
    }

    @Override
    public int getHeight() {
        ComboProgressStore.Snapshot snapshot = store.getSnapshot();
        int baseHeight = snapshot.available()
                ? getUnscaledHeight(
                        Minecraft.getInstance(),
                        getLines(snapshot, System.currentTimeMillis()).size(),
                        shouldShowBar(snapshot)
                )
                : EMPTY_HEIGHT;
        return Math.round(baseHeight * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().comboProgress.active && (store.getSnapshot().available() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Combo Progress";
    }

    private int getUnscaledWidth(Minecraft mc, List<RenderLine> lines) {
        int maxWidth = 0;
        for (RenderLine line : lines) {
            maxWidth = Math.max(maxWidth, mc.font.width(line.text()));
        }
        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight(Minecraft mc, int lineCount, boolean showBar) {
        int lineGaps = Math.max(0, lineCount - 1) * 2;
        int barSpace = showBar ? BAR_HEIGHT + 2 : 0;
        return PADDING_Y * 2 + mc.font.lineHeight * lineCount + lineGaps + barSpace;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.drawString(mc.font, "Combo x1.0 -> x1.1", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "0/1,000", PADDING_X, 16, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }

    private String formatMultiplier(double value) {
        return MULTIPLIER_FORMAT.format(value);
    }

    private String formatBlocks(int value) {
        return String.format(Locale.US, "%,d", value);
    }

    private List<RenderLine> getLines(ComboProgressStore.Snapshot snapshot, long now) {
        List<RenderLine> lines = new ArrayList<>(3);
        if (snapshot.maxed()) {
            lines.add(new RenderLine(
                    "Combo x" + formatMultiplier(snapshot.booster()) + " | MAX",
                    WidgetTheme.TITLE
            ));
            return lines;
        }

        lines.add(new RenderLine(
                "Combo x" + formatMultiplier(snapshot.booster()) + " -> x" + formatMultiplier(snapshot.nextBooster()),
                WidgetTheme.TITLE
        ));
        lines.add(new RenderLine(
                formatBlocks(snapshot.blocks()) + "/" + formatBlocks(snapshot.requiredBlocks()),
                snapshot.completed() ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SECONDARY
        ));

        long remainingSeconds = snapshot.remainingSeconds(now);
        if (remainingSeconds > 0L) {
            lines.add(new RenderLine("Expires in " + remainingSeconds + "s", WidgetTheme.STATUS_ERROR));
        }
        return lines;
    }

    private boolean shouldShowBar(ComboProgressStore.Snapshot snapshot) {
        return !snapshot.maxed() && ConfigManager.get().comboProgress.showBar;
    }

    private record RenderLine(String text, int color) {
    }
}
