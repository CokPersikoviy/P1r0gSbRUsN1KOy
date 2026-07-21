package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.level.LevelProgressStore;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class LevelProgressWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int BAR_HEIGHT = 3;
    private static final int EMPTY_WIDTH = 150;
    private static final int EMPTY_HEIGHT = 34;
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private final LevelProgressStore store;

    public LevelProgressWidget(int x, int y, HudLayer layer, LevelProgressStore store) {
        super(x, y, layer);
        this.store = store;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LevelProgressStore.LevelProgressSnapshot snapshot = store.getSnapshot();
        if (!snapshot.available()) {
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context, mc);
            return;
        }

        String title = snapshot.maxLevel()
                ? "Level " + snapshot.level() + " · MAX"
                : "Level " + snapshot.level();
        String blocksLine = "Blocks: " + formatInt(snapshot.blocks()) + "/" + formatInt(snapshot.requiredBlocks());
        String moneyLine = "Money: " + formatMoney(snapshot.money()) + "/" + formatMoney(snapshot.requiredMoney());
        int width = getUnscaledWidth(mc, title, blocksLine, moneyLine);
        int height = getUnscaledHeight(mc);
        int blocksColor = snapshot.blocks() >= snapshot.requiredBlocks() ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SECONDARY;
        int moneyColor = snapshot.money() >= snapshot.requiredMoney() ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SECONDARY;

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPanel(context, width, height);
        context.drawString(mc.font, title, PADDING_X, PADDING_Y, WidgetTheme.TITLE);
        context.drawString(mc.font, blocksLine, PADDING_X, PADDING_Y + mc.font.lineHeight + 2, blocksColor);
        context.drawString(mc.font, moneyLine, PADDING_X, PADDING_Y + (mc.font.lineHeight + 2) * 2, moneyColor);

        int barY = height - PADDING_Y - BAR_HEIGHT;
        HudSurface.drawBar(context, PADDING_X, barY, width - PADDING_X * 2, BAR_HEIGHT,
                (float) snapshot.progress(), WidgetTheme.BAR_FILL);

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        LevelProgressStore.LevelProgressSnapshot snapshot = store.getSnapshot();
        if (!snapshot.available()) {
            return Math.round(EMPTY_WIDTH * getScale());
        }

        Minecraft mc = Minecraft.getInstance();
        String title = snapshot.maxLevel() ? "Level " + snapshot.level() + " · MAX" : "Level " + snapshot.level();
        String blocksLine = "Blocks: " + formatInt(snapshot.blocks()) + "/" + formatInt(snapshot.requiredBlocks());
        String moneyLine = "Money: " + formatMoney(snapshot.money()) + "/" + formatMoney(snapshot.requiredMoney());
        return Math.round(getUnscaledWidth(mc, title, blocksLine, moneyLine) * getScale());
    }

    @Override
    public int getHeight() {
        LevelProgressStore.LevelProgressSnapshot snapshot = store.getSnapshot();
        int baseHeight = snapshot.available() ? getUnscaledHeight(Minecraft.getInstance()) : EMPTY_HEIGHT;
        return Math.round(baseHeight * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().levelProgress.active && (store.getSnapshot().available() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Level Progress";
    }

    private int getUnscaledWidth(Minecraft mc, String title, String blocksLine, String moneyLine) {
        int maxWidth = Math.max(mc.font.width(title), Math.max(mc.font.width(blocksLine), mc.font.width(moneyLine)));
        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight(Minecraft mc) {
        return PADDING_Y * 2 + mc.font.lineHeight * 3 + 4 + BAR_HEIGHT + 2;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.drawString(mc.font, "Level Progress", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "Waiting for levelinfo", PADDING_X, 16, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }

    private String formatInt(int value) {
        return formatCompactNumber(Math.max(0, value), false);
    }

    private String formatMoney(double value) {
        return formatCompactNumber(Math.max(0.0D, value), true);
    }

    private String formatCompactNumber(double value, boolean allowSmallK) {
        if (!allowSmallK && value < 10_000D) {
            return String.format(Locale.US, "%,d", (int) value);
        }
        if (allowSmallK && value < 1_000D) {
            return MONEY_FORMAT.format(value);
        }

        double[] divisors = {
                1_000_000_000_000_000_000D,
                1_000_000_000_000_000D,
                1_000_000_000_000D,
                1_000_000_000D,
                1_000_000D,
                1_000D
        };
        String[] suffixes = {"Q", "Qd", "T", "B", "M", "K"};

        for (int i = 0; i < divisors.length; i++) {
            if (value >= divisors[i]) {
                return MONEY_FORMAT.format(value / divisors[i]) + suffixes[i];
            }
        }

        return MONEY_FORMAT.format(value);
    }
}

