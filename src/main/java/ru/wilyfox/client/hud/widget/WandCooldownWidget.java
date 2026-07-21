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

public class WandCooldownWidget extends AbstractWidget {
    private static final int SLOT_SIZE = 20;
    private static final int ITEM_OFFSET = 2;
    private static final int BAR_HEIGHT = 2;
    private static final int GAP = 4;
    private static final int EMPTY_WIDTH = 98;
    private static final int EMPTY_HEIGHT = 24;

    private final WandCooldownTracker tracker;

    // Per-frame cache: getActiveEntries() rebuilds a merged+sorted list (with an ItemStack.copy per
    // entry) on every call, and render()/getWidth() both ask for it each frame (plus getWidth again in
    // the layout change-detector if anchored). Compute once per HUD frame, keyed on HudFrameClock.
    private long cachedFrameId = Long.MIN_VALUE;
    private List<WandCooldownEntry> cachedEntries;

    public WandCooldownWidget(int x, int y, HudLayer layer, WandCooldownTracker tracker) {
        super(x, y, layer);
        this.tracker = tracker;
    }

    private List<WandCooldownEntry> entries() {
        long frame = HudFrameClock.current();
        if (frame != cachedFrameId || cachedEntries == null) {
            cachedEntries = tracker.getActiveEntries();
            cachedFrameId = frame;
        }
        return cachedEntries;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!ConfigManager.get().wandCooldown.active) {
            return;
        }

        List<WandCooldownEntry> entries = entries();
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

        int width = getUnscaledWidth(entries.size());
        int height = getUnscaledHeight();

        HudSurface.drawPanel(context, width, height);

        int x = 0;
        for (WandCooldownEntry entry : entries) {
            context.fill(x, SLOT_SIZE - BAR_HEIGHT, x + SLOT_SIZE, SLOT_SIZE, WidgetTheme.BAR_BG);
            context.renderItem(entry.stack(), x + ITEM_OFFSET, ITEM_OFFSET);

            int fillWidth = Math.max(0, Math.min(SLOT_SIZE, Math.round(SLOT_SIZE * entry.progress())));
            if (fillWidth > 0) {
                context.fill(x, SLOT_SIZE - BAR_HEIGHT, x + fillWidth, SLOT_SIZE, WidgetTheme.BAR_FILL);
            }

            x += SLOT_SIZE + GAP;
        }

        context.pose().popPose();
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().wandCooldown.active && (tracker.hasActiveEntries() || isEditorPreview());
    }

    @Override
    public int getWidth() {
        return Math.round(getUnscaledWidth(entries().size()) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight() * getScale());
    }

    @Override
    public String getDisplayName() {
        return "Wand Cooldowns";
    }

    private int getUnscaledWidth(int count) {
        if (count <= 0) {
            return EMPTY_WIDTH;
        }

        return count * SLOT_SIZE + Math.max(0, count - 1) * GAP;
    }

    private int getUnscaledHeight() {
        return SLOT_SIZE;
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
        context.fill(0, SLOT_SIZE - BAR_HEIGHT, EMPTY_WIDTH, SLOT_SIZE, WidgetTheme.BAR_BG);

        context.drawString(mc.font, "Wand Cooldowns", 6, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No active wands", 6, 14, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }
}

