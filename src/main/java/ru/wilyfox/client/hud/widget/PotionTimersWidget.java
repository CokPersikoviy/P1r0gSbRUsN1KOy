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
    private static final String READY_TEXT = "\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u043e";
    private static final String AFTER_TEXT = "\u0447\u0435\u0440\u0435\u0437 ";
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXT_GAP = 5;
    private static final int ROW_GAP = 3;
    private static final int EMPTY_WIDTH = 138;
    private static final int EMPTY_HEIGHT = 28;

    private final PotionStore store;

    // Resolve names, icons and remaining times only once per HUD frame.
    private long cachedFrameId = Long.MIN_VALUE;
    private List<PotionStore.CooldownPotionEntry> cachedEntries;

    public PotionTimersWidget(int x, int y, HudLayer layer, PotionStore store) {
        super(x, y, layer);
        this.store = store;
    }

    private List<PotionStore.CooldownPotionEntry> entries() {
        long frame = HudFrameClock.current();
        if (frame != cachedFrameId || cachedEntries == null) {
            List<PotionStore.CooldownPotionEntry> activeEntries = store.getCooldownEntries(graceMillis());
            int limit = Math.max(1, Math.min(15, ConfigManager.get().potionTimers.maxEntries));
            cachedEntries = activeEntries.size() <= limit
                    ? activeEntries
                    : List.copyOf(activeEntries.subList(0, limit));
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
        List<PotionStore.CooldownPotionEntry> entries = entries();

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
            context.drawString(mc.font, "Potion Cooldowns", PADDING_X, y, WidgetTheme.TITLE);
            y += mc.font.lineHeight + 4;
        }

        for (PotionStore.CooldownPotionEntry entry : entries) {
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
        return ConfigManager.get().potionTimers.active && (store.hasCooldownEntries(graceMillis()) || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Potion Cooldowns";
    }

    private void renderRow(GuiGraphics context, Minecraft mc, PotionStore.CooldownPotionEntry entry, int width, int y, int rowHeight) {
        int iconX = PADDING_X;
        int iconY = y + Math.max(0, (rowHeight - ICON_SIZE) / 2);
        if (showIcons()) {
            ItemStack icon = entry.icon();
            if (!icon.isEmpty()) {
                context.renderItem(icon, iconX, iconY);
            }
        }

        boolean ready = entry.remainingMillis() <= 0L;
        String nameText = entry.name() + ":";
        String timeText = ready ? READY_TEXT : AFTER_TEXT + formatTimer(entry.remainingMillis());

        int textX = PADDING_X + iconBlockWidth();
        int textY = y + Math.max(0, (rowHeight - mc.font.lineHeight) / 2);
        int timeWidth = mc.font.width(timeText);
        int rightX = width - PADDING_X;

        context.drawString(mc.font, nameText, textX, textY, ready ? WidgetTheme.TEXT_MUTED : WidgetTheme.TEXT_SOFT);
        context.drawString(mc.font, timeText, rightX - timeWidth, textY, ready ? WidgetTheme.STATUS_SUCCESS : WidgetTheme.TEXT_SECONDARY);
    }

    private static String formatTimer(long remainingMillis) {
        return Formatting.formatMillis(System.currentTimeMillis() + remainingMillis);
    }

    private int getUnscaledWidth(List<PotionStore.CooldownPotionEntry> entries) {
        if (entries.isEmpty()) {
            return EMPTY_WIDTH;
        }

        Minecraft mc = Minecraft.getInstance();
        int maxWidth = WidgetUtils.showWidgetTitles() ? mc.font.width("Potion Cooldowns") : 0;

        for (PotionStore.CooldownPotionEntry entry : entries) {
            String stateText = entry.remainingMillis() <= 0L ? READY_TEXT : AFTER_TEXT + formatTimer(entry.remainingMillis());
            int lineWidth = iconBlockWidth()
                    + mc.font.width(entry.name() + ":")
                    + 8
                    + mc.font.width(stateText);
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

    private static boolean showIcons() {
        return ConfigManager.get().potionTimers.showIcons;
    }

    private static int iconBlockWidth() {
        return showIcons() ? ICON_SIZE + ICON_TEXT_GAP : 0;
    }

    private static long graceMillis() {
        return Math.max(0, ConfigManager.get().potionTimers.belowZeroSeconds) * 1000L;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.drawString(mc.font, "Potion Cooldowns", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No potion cooldowns", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }
}
