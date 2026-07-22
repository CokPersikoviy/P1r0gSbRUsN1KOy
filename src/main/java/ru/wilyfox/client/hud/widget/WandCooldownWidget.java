package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.internal.HudFrameClock;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.wand.WandCooldownTracker;
import ru.wilyfox.client.wand.WandCooldownTracker.WandCooldownEntry;

import java.util.List;
import java.util.Locale;

public class WandCooldownWidget extends AbstractWidget {
    private static final int SLOT_SIZE = 20;
    private static final int ITEM_OFFSET = 2;
    private static final int BAR_HEIGHT = 2;
    private static final int GAP = 4;
    private static final int NUMERIC_SLOT_WIDTH = 32;
    private static final int NUMERIC_TEXT_GAP = 1;
    private static final int NUMERIC_BOTTOM_PADDING = 2;
    private static final int EMPTY_WIDTH = 98;
    private static final int EMPTY_HEIGHT = 24;

    private final WandCooldownTracker tracker;

    // Per-frame cache: getActiveEntries() rebuilds a merged+sorted list (with an ItemStack.copy per
    // entry) on every call, and render()/getWidth() both ask for it each frame (plus getWidth again in
    // the layout change-detector if anchored). Compute once per HUD frame, keyed on HudFrameClock.
    private long cachedFrameId = Long.MIN_VALUE;
    private List<WandCooldownEntry> cachedEntries;
    private boolean cachedNumericMode;

    public WandCooldownWidget(int x, int y, HudLayer layer, WandCooldownTracker tracker) {
        super(x, y, layer);
        this.tracker = tracker;
    }

    private List<WandCooldownEntry> entries() {
        long frame = HudFrameClock.current();
        boolean numericMode = ConfigManager.get().wandCooldown.numericCooldown;
        if (frame != cachedFrameId || cachedEntries == null || cachedNumericMode != numericMode) {
            cachedEntries = tracker.getActiveEntries(numericMode);
            cachedFrameId = frame;
            cachedNumericMode = numericMode;
        }
        return cachedEntries;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!ConfigManager.get().wandCooldown.active) {
            return;
        }

        List<WandCooldownEntry> entries = entries();
        boolean numericMode = ConfigManager.get().wandCooldown.numericCooldown;
        if (entries.isEmpty()) {
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context);
            return;
        }

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0.0f);
        context.pose().scale(scale, scale, 1.0f);

        int width = getUnscaledWidth(entries.size(), numericMode);
        int height = getUnscaledHeight(entries.size(), numericMode);

        HudSurface.drawPanel(context, width, height);

        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        int slotWidth = numericMode ? NUMERIC_SLOT_WIDTH : SLOT_SIZE;
        int x = 0;
        for (WandCooldownEntry entry : entries) {
            if (numericMode) {
                context.renderItem(entry.stack(), x + (slotWidth - 16) / 2, ITEM_OFFSET);

                long remainingMillis = Math.max(0L, entry.endsAt() - now);
                String remaining = formatNumericCooldown(remainingMillis);
                int textX = x + (slotWidth - minecraft.font.width(remaining)) / 2;
                int textColor = remainingMillis <= 1_000L
                        ? WidgetTheme.HARD_ACCENT
                        : WidgetTheme.TEXT_SECONDARY;
                context.drawString(minecraft.font, remaining, textX,
                        SLOT_SIZE + NUMERIC_TEXT_GAP, textColor, false);
            } else {
                context.fill(x, SLOT_SIZE - BAR_HEIGHT, x + SLOT_SIZE, SLOT_SIZE, WidgetTheme.BAR_BG);
                context.renderItem(entry.stack(), x + ITEM_OFFSET, ITEM_OFFSET);

                int fillWidth = Math.max(0, Math.min(SLOT_SIZE, Math.round(SLOT_SIZE * entry.progress())));
                if (fillWidth > 0) {
                    context.fill(x, SLOT_SIZE - BAR_HEIGHT, x + fillWidth, SLOT_SIZE, WidgetTheme.BAR_FILL);
                }
            }

            x += slotWidth + GAP;
        }

        context.pose().popPose();
    }

    @Override
    public boolean isVisible() {
        boolean numericMode = ConfigManager.get().wandCooldown.numericCooldown;
        return ConfigManager.get().wandCooldown.active
                && (tracker.hasActiveEntries(numericMode) || isEditorPreview());
    }

    @Override
    public int getWidth() {
        return Math.round(getUnscaledWidth(
                entries().size(),
                ConfigManager.get().wandCooldown.numericCooldown
        ) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight(
                entries().size(),
                ConfigManager.get().wandCooldown.numericCooldown
        ) * getScale());
    }

    @Override
    public String getDisplayName() {
        return "Wand Cooldowns";
    }

    private int getUnscaledWidth(int count, boolean numericMode) {
        if (count <= 0) {
            return EMPTY_WIDTH;
        }

        int slotWidth = numericMode ? NUMERIC_SLOT_WIDTH : SLOT_SIZE;
        return count * slotWidth + Math.max(0, count - 1) * GAP;
    }

    private int getUnscaledHeight(int count, boolean numericMode) {
        if (count <= 0) {
            return EMPTY_HEIGHT;
        }
        if (!numericMode) {
            return SLOT_SIZE;
        }
        return SLOT_SIZE + NUMERIC_TEXT_GAP
                + Minecraft.getInstance().font.lineHeight
                + NUMERIC_BOTTOM_PADDING;
    }

    static String formatNumericCooldown(long remainingMillis) {
        long clamped = Math.max(0L, remainingMillis);
        if (clamped <= 1_000L) {
            double tenths = Math.ceil(clamped / 100.0D) / 10.0D;
            return String.format(Locale.ROOT, "%.1fs", tenths);
        }

        long totalSeconds = (clamped + 999L) / 1_000L;
        if (totalSeconds < 60L) {
            return totalSeconds + "s";
        }
        if (totalSeconds < 3_600L) {
            return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L);
        }
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 3_600L, totalSeconds % 3_600L / 60L);
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphics context) {
        Minecraft mc = Minecraft.getInstance();

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0.0f);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        if (!ConfigManager.get().wandCooldown.numericCooldown) {
            context.fill(0, SLOT_SIZE - BAR_HEIGHT, EMPTY_WIDTH, SLOT_SIZE, WidgetTheme.BAR_BG);
        }

        context.drawString(mc.font, "Wand Cooldowns", 6, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No active wands", 6, 14, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }
}
