package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.miner.ActiveMinerInfo;
import ru.wilyfox.client.miner.ActiveMinersStore;
import ru.wilyfox.utils.Formatting;

import java.util.List;
import java.util.Locale;

public class ActiveMinersWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int EMPTY_WIDTH = 124;
    private static final int EMPTY_HEIGHT = 28;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXT_GAP = 5;
    private static final int ROW_GAP = 3;

    private final ActiveMinersStore store;

    public ActiveMinersWidget(int x, int y, HudLayer layer, ActiveMinersStore store) {
        super(x, y, layer);
        this.store = store;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<ActiveMinerInfo> miners = store.getAll();

        if (miners.isEmpty()) {
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context, mc);
            return;
        }

        int width = getUnscaledWidth();
        int height = getUnscaledHeight();
        int rowHeight = Math.max(ICON_SIZE, mc.font.lineHeight);

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        context.fill(0, 0, width, height, WidgetTheme.WIDGET_PANEL_BG);
        context.fill(0, 0, width, 1, WidgetTheme.WIDGET_ACCENT_LINE);

        int y = PADDING_Y;
        context.drawString(mc.font, "Miners", PADDING_X, y, WidgetTheme.TITLE);
        y += mc.font.lineHeight + 4;

        for (ActiveMinerInfo miner : miners) {
            renderMinerRow(context, mc, miner, width, y, rowHeight);
            y += rowHeight + ROW_GAP;
        }

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        return Math.round(getUnscaledWidth() * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight() * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().activeMiners.active && (!store.isEmpty() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Miners";
    }

    private int getUnscaledWidth() {
        List<ActiveMinerInfo> miners = store.getAll();
        if (miners.isEmpty()) {
            return EMPTY_WIDTH;
        }

        Minecraft mc = Minecraft.getInstance();
        int maxWidth = mc.font.width("Miners");

        for (ActiveMinerInfo miner : miners) {
            int rowWidth = ICON_SIZE + ICON_TEXT_GAP
                    + mc.font.width(formatLevel(miner.level()))
                    + (miner.level() > 0 ? 4 : 0)
                    + mc.font.width(miner.resource())
                    + 10
                    + mc.font.width(formatState(miner));
            maxWidth = Math.max(maxWidth, rowWidth);
        }

        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight() {
        int count = store.getAll().size();
        if (count == 0) {
            return EMPTY_HEIGHT;
        }

        int rowHeight = Math.max(ICON_SIZE, Minecraft.getInstance().font.lineHeight);
        return PADDING_Y * 2
                + Minecraft.getInstance().font.lineHeight
                + 4
                + count * rowHeight
                + Math.max(0, count - 1) * ROW_GAP;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        context.fill(0, 0, EMPTY_WIDTH, EMPTY_HEIGHT, WidgetTheme.WIDGET_PANEL_BG_SOFT);
        context.fill(0, 0, EMPTY_WIDTH, 1, WidgetTheme.WIDGET_ACCENT_LINE);
        context.drawString(mc.font, "Miners", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No active miners", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }

    private void renderMinerRow(GuiGraphics context, Minecraft mc, ActiveMinerInfo miner, int width, int y, int rowHeight) {
        int iconX = PADDING_X;
        int iconY = y + Math.max(0, (rowHeight - ICON_SIZE) / 2);
        ItemStack icon = miner.icon();
        if (!icon.isEmpty()) {
            context.renderItem(icon, iconX, iconY);
        }

        String levelText = formatLevel(miner.level());
        String resourceText = miner.resource();
        String stateText = formatState(miner);
        int textBaseY = y + Math.max(0, (rowHeight - mc.font.lineHeight) / 2);
        int textX = iconX + ICON_SIZE + ICON_TEXT_GAP;
        int levelWidth = mc.font.width(levelText);
        int stateWidth = mc.font.width(stateText);
        int resourceX = textX + levelWidth + (miner.level() > 0 ? 4 : 0);
        int stateX = width - PADDING_X - stateWidth;

        if (!levelText.isEmpty()) {
            context.drawString(mc.font, levelText, textX, textBaseY, WidgetTheme.TEXT_SECONDARY);
        }
        context.drawString(mc.font, resourceText, resourceX, textBaseY, WidgetTheme.TEXT_PRIMARY);
        context.drawString(mc.font, stateText, stateX, textBaseY, getLineColor(miner));
    }

    private String formatLevel(int level) {
        return level > 0 ? "Lv." + level : "";
    }

    private String formatState(ActiveMinerInfo miner) {
        if (isDead(miner.status())) {
            return "\u041f\u043e\u0433\u0438\u0431";
        }

        if (isComplete(miner.status())) {
            return "\u0412\u0435\u0440\u043d\u0443\u043b\u0441\u044f";
        }

        long remaining = miner.homecomingAt() - System.currentTimeMillis();
        if (remaining > 0L) {
            return Formatting.formatMillis(System.currentTimeMillis() + remaining);
        }

        return prettifyStatus(miner.status());
    }

    private int getLineColor(ActiveMinerInfo miner) {
        if (isDead(miner.status())) {
            return WidgetTheme.TEXT_SECONDARY;
        }

        if (isComplete(miner.status())) {
            return WidgetTheme.TEXT_PRIMARY;
        }

        return WidgetTheme.TEXT_SOFT;
    }

    private boolean isDead(String status) {
        String normalized = status == null ? "" : status.toUpperCase(Locale.ROOT);
        return normalized.contains("DEAD") || normalized.contains("DIED") || normalized.contains("KILLED");
    }

    private boolean isComplete(String status) {
        return "COMPLETE_TRAVEL".equalsIgnoreCase(status);
    }

    private String prettifyStatus(String status) {
        String normalized = status == null ? "" : status.trim();
        if (normalized.isEmpty()) {
            return "-";
        }

        String[] parts = normalized.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }

        return builder.isEmpty() ? normalized : builder.toString();
    }
}

