package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.booster.BoosterStore;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.utils.Formatting;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class BoostersWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int ROW_GAP = 4;
    private static final int BAR_HEIGHT = 2;
    private static final DecimalFormat MULTIPLIER_FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final Integer[] BASE_VALUES = {10, 12, 15, 20, 25, 30, 35, 40, 50};

    private final BoosterStore store;

    public BoostersWidget(int x, int y, HudLayer layer, BoosterStore store) {
        super(x, y, layer);
        this.store = store;
    }

    public static Integer[] baseValues() {
        return BASE_VALUES;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (ConfigManager.get().boosters.compact) {
            renderCompact(context, mc);
        } else {
            renderDetailed(context, mc);
        }
    }

    @Override
    public int getWidth() {
        Minecraft mc = Minecraft.getInstance();
        return Math.round((ConfigManager.get().boosters.compact ? getCompactWidth(mc) : getDetailedWidth(mc)) * getScale());
    }

    @Override
    public int getHeight() {
        Minecraft mc = Minecraft.getInstance();
        return Math.round((ConfigManager.get().boosters.compact ? getCompactHeight(mc) : getDetailedHeight(mc)) * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().boosters.active && (store.hasAnyActive() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Boosters";
    }

    private void renderCompact(GuiGraphics context, Minecraft mc) {
        BoosterStore.Snapshot money = store.getSnapshot(BoosterStore.Kind.MONEY);
        BoosterStore.Snapshot shards = store.getSnapshot(BoosterStore.Kind.SHARDS);
        int width = getCompactWidth(mc);
        int height = getCompactHeight(mc);

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        context.fill(0, 0, width, height, WidgetTheme.WIDGET_PANEL_BG);
        context.fill(0, 0, width, 1, WidgetTheme.WIDGET_ACCENT_LINE);

        int y = PADDING_Y;
        context.drawString(mc.font, "Boosters", PADDING_X, y, WidgetTheme.TITLE);
        y += mc.font.lineHeight + 4;

        y = renderCompactRow(context, mc, "Money Booster", money, getBaseMultiplier(ConfigManager.get().boosters.moneyBaseTenths), width, y);
        renderCompactRow(context, mc, "Shards Booster", shards, getBaseMultiplier(ConfigManager.get().boosters.shardsBaseTenths), width, y);

        context.pose().popPose();
    }

    private int renderCompactRow(GuiGraphics context, Minecraft mc, String label, BoosterStore.Snapshot snapshot, double baseMultiplier, int width, int y) {
        String text = label + ": x" + formatMultiplier(snapshot.totalMultiplier(baseMultiplier));
        context.drawString(mc.font, text, PADDING_X, y, WidgetTheme.TEXT_SOFT);
        y += mc.font.lineHeight + 2;

        int barLeft = PADDING_X;
        int barRight = width - PADDING_X;
        context.fill(barLeft, y, barRight, y + BAR_HEIGHT, WidgetTheme.BAR_BG);

        BoosterStore.Entry nearest = snapshot.nearest();
        if (nearest != null) {
            int innerWidth = barRight - barLeft;
            int fillWidth = Math.max(0, Math.min(innerWidth, Math.round(innerWidth * nearest.progress())));
            if (fillWidth > 0) {
                context.fill(barLeft, y, barLeft + fillWidth, y + BAR_HEIGHT, WidgetTheme.BAR_FILL);
            }
        }

        return y + BAR_HEIGHT + ROW_GAP;
    }

    private void renderDetailed(GuiGraphics context, Minecraft mc) {
        BoosterStore.Snapshot money = store.getSnapshot(BoosterStore.Kind.MONEY);
        BoosterStore.Snapshot shards = store.getSnapshot(BoosterStore.Kind.SHARDS);
        int width = getDetailedWidth(mc);
        int height = getDetailedHeight(mc);
        int columnWidth = (width - PADDING_X * 2 - 8) / 2;

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        context.fill(0, 0, width, height, WidgetTheme.WIDGET_PANEL_BG);
        context.fill(0, 0, width, 1, WidgetTheme.WIDGET_ACCENT_LINE);

        context.drawString(mc.font, "Boosters", PADDING_X, PADDING_Y, WidgetTheme.TITLE);

        int contentY = PADDING_Y + mc.font.lineHeight + 4;
        renderDetailedColumn(context, mc, "Money", money, getBaseMultiplier(ConfigManager.get().boosters.moneyBaseTenths), PADDING_X, contentY, columnWidth);
        renderDetailedColumn(context, mc, "Shards", shards, getBaseMultiplier(ConfigManager.get().boosters.shardsBaseTenths), PADDING_X + columnWidth + 8, contentY, columnWidth);

        context.pose().popPose();
    }

    private void renderDetailedColumn(GuiGraphics context, Minecraft mc, String title, BoosterStore.Snapshot snapshot, double baseMultiplier, int x, int y, int width) {
        context.drawString(mc.font, title + " x" + formatMultiplier(snapshot.totalMultiplier(baseMultiplier)), x, y, WidgetTheme.TEXT_PRIMARY);
        y += mc.font.lineHeight + 2;

        if (snapshot.entries().isEmpty()) {
            context.drawString(mc.font, "No active boosts", x, y, WidgetTheme.TEXT_MUTED);
            return;
        }

        for (BoosterStore.Entry entry : snapshot.entries()) {
            renderBoosterLine(context, mc, x, y, width, "Active", entry);
            y += mc.font.lineHeight + 1;
        }
    }

    private void renderBoosterLine(GuiGraphics context, Minecraft mc, int x, int y, int width, String label, BoosterStore.Entry entry) {
        String left = label + " x" + formatMultiplier(entry.multiplier());
        String right = Formatting.formatMillis(System.currentTimeMillis() + entry.remainingMillis());
        context.drawString(mc.font, left, x, y, WidgetTheme.TEXT_SOFT);
        context.drawString(mc.font, right, x + width - mc.font.width(right), y, WidgetTheme.TEXT_SECONDARY);
    }

    private int getCompactWidth(Minecraft mc) {
        BoosterStore.Snapshot money = store.getSnapshot(BoosterStore.Kind.MONEY);
        BoosterStore.Snapshot shards = store.getSnapshot(BoosterStore.Kind.SHARDS);
        int maxWidth = mc.font.width("Boosters");
        maxWidth = Math.max(maxWidth, mc.font.width("Money Booster: x" + formatMultiplier(money.totalMultiplier(getBaseMultiplier(ConfigManager.get().boosters.moneyBaseTenths)))));
        maxWidth = Math.max(maxWidth, mc.font.width("Shards Booster: x" + formatMultiplier(shards.totalMultiplier(getBaseMultiplier(ConfigManager.get().boosters.shardsBaseTenths)))));
        return maxWidth + PADDING_X * 2;
    }

    private int getCompactHeight(Minecraft mc) {
        return PADDING_Y * 2 + mc.font.lineHeight + 4 + 2 * (mc.font.lineHeight + 2 + BAR_HEIGHT) + ROW_GAP;
    }

    private int getDetailedWidth(Minecraft mc) {
        BoosterStore.Snapshot money = store.getSnapshot(BoosterStore.Kind.MONEY);
        BoosterStore.Snapshot shards = store.getSnapshot(BoosterStore.Kind.SHARDS);
        int moneyWidth = getDetailedColumnWidth(mc, "Money", money, getBaseMultiplier(ConfigManager.get().boosters.moneyBaseTenths));
        int shardsWidth = getDetailedColumnWidth(mc, "Shards", shards, getBaseMultiplier(ConfigManager.get().boosters.shardsBaseTenths));
        return PADDING_X * 2 + moneyWidth + shardsWidth + 8;
    }

    private int getDetailedColumnWidth(Minecraft mc, String title, BoosterStore.Snapshot snapshot, double baseMultiplier) {
        int maxWidth = mc.font.width(title + " x" + formatMultiplier(snapshot.totalMultiplier(baseMultiplier)));
        maxWidth = Math.max(maxWidth, mc.font.width("No active boosts"));
        for (BoosterStore.Entry entry : snapshot.entries()) {
            maxWidth = Math.max(maxWidth, mc.font.width("Active x" + formatMultiplier(entry.multiplier()) + " " + Formatting.formatMillis(System.currentTimeMillis() + entry.remainingMillis())));
        }
        return maxWidth;
    }

    private int getDetailedHeight(Minecraft mc) {
        int moneyLines = getDetailedLines(store.getSnapshot(BoosterStore.Kind.MONEY));
        int shardsLines = getDetailedLines(store.getSnapshot(BoosterStore.Kind.SHARDS));
        int lines = Math.max(moneyLines, shardsLines);
        return PADDING_Y * 2 + mc.font.lineHeight + 4 + lines * (mc.font.lineHeight + 1);
    }

    private int getDetailedLines(BoosterStore.Snapshot snapshot) {
        int lines = 1;
        return lines + Math.max(1, snapshot.entries().size());
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private static double getBaseMultiplier(int tenths) {
        return Math.max(0.1D, tenths / 10.0D);
    }

    private static String formatMultiplier(double multiplier) {
        return MULTIPLIER_FORMAT.format(multiplier);
    }
}

