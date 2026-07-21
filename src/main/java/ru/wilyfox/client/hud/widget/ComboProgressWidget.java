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

        String title = snapshot.maxed()
                ? "Combo x" + formatMultiplier(snapshot.booster()) + " · MAX"
                : "Combo x" + formatMultiplier(snapshot.booster()) + " -> x" + formatMultiplier(snapshot.nextBooster());
        String progressLine = formatBlocks(snapshot.blocks()) + "/" + formatBlocks(snapshot.requiredBlocks());
        int width = getUnscaledWidth(mc, title, progressLine);
        int height = getUnscaledHeight(mc);
        int progressColor = snapshot.progress() >= 1.0D ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SECONDARY;

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPanel(context, width, height);
        context.drawString(mc.font, title, PADDING_X, PADDING_Y, WidgetTheme.TITLE);
        context.drawString(mc.font, progressLine, PADDING_X, PADDING_Y + mc.font.lineHeight + 2, progressColor);

        int barY = height - PADDING_Y - BAR_HEIGHT;
        HudSurface.drawBar(context, PADDING_X, barY, width - PADDING_X * 2, BAR_HEIGHT,
                (float) snapshot.progress(), WidgetTheme.BAR_FILL);

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        ComboProgressStore.Snapshot snapshot = store.getSnapshot();
        if (!snapshot.available()) {
            return Math.round(EMPTY_WIDTH * getScale());
        }

        Minecraft mc = Minecraft.getInstance();
        String title = snapshot.maxed()
                ? "Combo x" + formatMultiplier(snapshot.booster()) + " · MAX"
                : "Combo x" + formatMultiplier(snapshot.booster()) + " -> x" + formatMultiplier(snapshot.nextBooster());
        String progressLine = formatBlocks(snapshot.blocks()) + "/" + formatBlocks(snapshot.requiredBlocks());
        return Math.round(getUnscaledWidth(mc, title, progressLine) * getScale());
    }

    @Override
    public int getHeight() {
        ComboProgressStore.Snapshot snapshot = store.getSnapshot();
        int baseHeight = snapshot.available() ? getUnscaledHeight(Minecraft.getInstance()) : EMPTY_HEIGHT;
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

    private int getUnscaledWidth(Minecraft mc, String title, String progressLine) {
        int maxWidth = Math.max(mc.font.width(title), mc.font.width(progressLine));
        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight(Minecraft mc) {
        return PADDING_Y * 2 + mc.font.lineHeight * 2 + 2 + BAR_HEIGHT + 2;
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
        return String.format(Locale.US, "%,d", Math.max(0, value));
    }
}

