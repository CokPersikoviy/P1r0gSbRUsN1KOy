package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.potion.PotionStore;
import ru.wilyfox.utils.Formatting;

import java.util.List;

public class PotionTimersWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXT_GAP = 5;
    private static final int ROW_GAP = 3;
    private static final int EMPTY_WIDTH = 124;
    private static final int EMPTY_HEIGHT = 28;

    private final PotionStore store;

    public PotionTimersWidget(int x, int y, HudLayer layer, PotionStore store) {
        super(x, y, layer);
        this.store = store;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<PotionStore.ActivePotionEntry> entries = store.getActiveEntries();

        if (entries.isEmpty()) {
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context, mc);
            return;
        }

        int width = getUnscaledWidth(entries);
        int height = getUnscaledHeight(entries.size());
        int rowHeight = Math.max(ICON_SIZE, mc.font.lineHeight);

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        context.fill(0, 0, width, height, WidgetTheme.WIDGET_PANEL_BG);
        context.fill(0, 0, width, 1, WidgetTheme.WIDGET_ACCENT_LINE);

        int y = PADDING_Y;
        context.drawString(mc.font, "Potions", PADDING_X, y, WidgetTheme.TITLE);
        y += mc.font.lineHeight + 4;

        for (PotionStore.ActivePotionEntry entry : entries) {
            renderRow(context, mc, entry, width, y, rowHeight);
            y += rowHeight + ROW_GAP;
        }

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        return Math.round(getUnscaledWidth(store.getActiveEntries()) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight(store.getActiveEntries().size()) * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().potionTimers.active && (store.hasActiveEntries() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Potions";
    }

    private void renderRow(GuiGraphics context, Minecraft mc, PotionStore.ActivePotionEntry entry, int width, int y, int rowHeight) {
        int iconX = PADDING_X;
        int iconY = y + Math.max(0, (rowHeight - ICON_SIZE) / 2);
        ItemStack icon = entry.icon();
        if (!icon.isEmpty()) {
            context.renderItem(icon, iconX, iconY);
        }

        String nameText = entry.name() + " [" + entry.quality() + "%]";
        String timeText = Formatting.formatMillis(System.currentTimeMillis() + entry.remainingMillis());

        int textX = iconX + ICON_SIZE + ICON_TEXT_GAP;
        int textY = y + Math.max(0, (rowHeight - mc.font.lineHeight) / 2);
        int timeWidth = mc.font.width(timeText);
        int rightX = width - PADDING_X;

        context.drawString(mc.font, nameText, textX, textY, WidgetTheme.TEXT_SOFT);
        context.drawString(mc.font, timeText, rightX - timeWidth, textY, WidgetTheme.TEXT_SECONDARY);
    }

    private int getUnscaledWidth(List<PotionStore.ActivePotionEntry> entries) {
        if (entries.isEmpty()) {
            return EMPTY_WIDTH;
        }

        Minecraft mc = Minecraft.getInstance();
        int maxWidth = mc.font.width("Potions");

        for (PotionStore.ActivePotionEntry entry : entries) {
            int lineWidth = ICON_SIZE + ICON_TEXT_GAP
                    + mc.font.width(entry.name() + " [" + entry.quality() + "%]")
                    + 8
                    + mc.font.width(Formatting.formatMillis(System.currentTimeMillis() + entry.remainingMillis()));
            maxWidth = Math.max(maxWidth, lineWidth);
        }

        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight(int count) {
        if (count <= 0) {
            return EMPTY_HEIGHT;
        }

        int rowHeight = Math.max(ICON_SIZE, Minecraft.getInstance().font.lineHeight);
        return PADDING_Y * 2 + Minecraft.getInstance().font.lineHeight + 4 + count * rowHeight + Math.max(0, count - 1) * ROW_GAP;
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
        context.drawString(mc.font, "Potions", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No active potions", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }
}

