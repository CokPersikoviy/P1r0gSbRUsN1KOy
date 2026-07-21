package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.internal.HudFrameClock;
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

    // Per-frame cache: render()/getWidth()/getHeight() each call getActiveEntries() (which rebuilds a
    // list) every frame; compute once per HUD frame, keyed on HudFrameClock.
    private long cachedFrameId = Long.MIN_VALUE;
    private List<PotionStore.ActivePotionEntry> cachedEntries;

    public PotionTimersWidget(int x, int y, HudLayer layer, PotionStore store) {
        super(x, y, layer);
        this.store = store;
    }

    private List<PotionStore.ActivePotionEntry> entries() {
        long frame = HudFrameClock.current();
        if (frame != cachedFrameId || cachedEntries == null) {
            cachedEntries = store.getActiveEntries();
            cachedFrameId = frame;
        }
        return cachedEntries;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<PotionStore.ActivePotionEntry> entries = entries();

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

        HudSurface.drawPanel(context, width, height);

        int y = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.drawString(mc.font, "Potions", PADDING_X, y, WidgetTheme.TITLE);
            y += mc.font.lineHeight + 4;
        }

        for (PotionStore.ActivePotionEntry entry : entries) {
            renderRow(context, mc, entry, width, y, rowHeight);
            y += rowHeight + ROW_GAP;
        }

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        return Math.round(getUnscaledWidth(entries()) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight(entries().size()) * getScale());
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

        boolean expired = entry.remainingMillis() < 0L;
        String nameText = entry.name() + " [" + entry.quality() + "%]";
        String timeText = formatTimer(entry.remainingMillis());

        int textX = iconX + ICON_SIZE + ICON_TEXT_GAP;
        int textY = y + Math.max(0, (rowHeight - mc.font.lineHeight) / 2);
        int timeWidth = mc.font.width(timeText);
        int rightX = width - PADDING_X;

        context.drawString(mc.font, nameText, textX, textY, expired ? WidgetTheme.TEXT_MUTED : WidgetTheme.TEXT_SOFT);
        context.drawString(mc.font, timeText, rightX - timeWidth, textY, expired ? WidgetTheme.TEXT_MUTED : WidgetTheme.TEXT_SECONDARY);
    }

    /** Signed "−MM:SS" while an expired potion is in its below-zero grace window, "MM:SS" otherwise. */
    private static String formatTimer(long remainingMillis) {
        long endsAt = System.currentTimeMillis() + remainingMillis;
        return remainingMillis < 0L ? Formatting.formatMillisSigned(endsAt) : Formatting.formatMillis(endsAt);
    }

    private int getUnscaledWidth(List<PotionStore.ActivePotionEntry> entries) {
        if (entries.isEmpty()) {
            return EMPTY_WIDTH;
        }

        Minecraft mc = Minecraft.getInstance();
        int maxWidth = WidgetUtils.showWidgetTitles() ? mc.font.width("Potions") : 0;

        for (PotionStore.ActivePotionEntry entry : entries) {
            int lineWidth = ICON_SIZE + ICON_TEXT_GAP
                    + mc.font.width(entry.name() + " [" + entry.quality() + "%]")
                    + 8
                    + mc.font.width(formatTimer(entry.remainingMillis()));
            maxWidth = Math.max(maxWidth, lineWidth);
        }

        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight(int count) {
        if (count <= 0) {
            return EMPTY_HEIGHT;
        }

        int rowHeight = Math.max(ICON_SIZE, Minecraft.getInstance().font.lineHeight);
        int titleBlock = WidgetUtils.showWidgetTitles() ? Minecraft.getInstance().font.lineHeight + 4 : 0;
        return PADDING_Y * 2 + titleBlock + count * rowHeight + Math.max(0, count - 1) * ROW_GAP;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.drawString(mc.font, "Potions", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No active potions", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }
}

