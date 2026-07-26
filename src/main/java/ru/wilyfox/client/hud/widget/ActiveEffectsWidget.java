package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.effect.ActiveEffectKind;
import ru.wilyfox.client.effect.ActiveEffectStore;
import ru.wilyfox.client.effect.ActiveEffectStore.Entry;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.internal.HudFrameClock;
import ru.wilyfox.client.hud.layer.HudLayer;

import java.util.List;
import java.util.Locale;

public final class ActiveEffectsWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int ROW_GAP = 3;
    private static final int TEXT_GAP = 8;
    private static final int EMPTY_WIDTH = 112;
    private static final int EMPTY_HEIGHT = 28;

    private final ActiveEffectStore store;
    private long cachedFrameId = Long.MIN_VALUE;
    private List<Entry> cachedEntries;

    public ActiveEffectsWidget(int x, int y, HudLayer layer, ActiveEffectStore store) {
        super(x, y, layer);
        this.store = store;
    }

    private List<Entry> entries() {
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
        List<Entry> entries = entries();
        if (entries.isEmpty()) {
            if (isEditorPreview()) {
                renderPlaceholder(context, mc);
            }
            return;
        }

        int width = getUnscaledWidth(entries);
        int height = getUnscaledHeight(entries.size());

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0F);

        HudSurface.drawPanel(context, width, height);

        int y = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.drawString(mc.font, "Active Effects", PADDING_X, y, WidgetTheme.TITLE);
            y += mc.font.lineHeight + ROW_GAP;
        }

        for (Entry entry : entries) {
            String remaining = formatRemaining(entry.remainingMillis());
            int timeWidth = mc.font.width(remaining);
            int nameColor = entry.kind() == ActiveEffectKind.DEBUFF
                    ? WidgetTheme.HARD_ACCENT
                    : WidgetTheme.STATUS_SUCCESS;

            context.drawString(mc.font, entry.displayName(), PADDING_X, y, nameColor);
            context.drawString(
                    mc.font,
                    remaining,
                    width - PADDING_X - timeWidth,
                    y,
                    WidgetTheme.TEXT_SECONDARY
            );
            y += mc.font.lineHeight + ROW_GAP;
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
        return ConfigManager.get().activeEffects.active && (store.hasActiveEntries() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Active Effects";
    }

    private int getUnscaledWidth(List<Entry> entries) {
        if (entries.isEmpty()) {
            return EMPTY_WIDTH;
        }

        Minecraft mc = Minecraft.getInstance();
        int maxWidth = WidgetUtils.showWidgetTitles() ? mc.font.width("Active Effects") : 0;
        for (Entry entry : entries) {
            int lineWidth = mc.font.width(entry.displayName())
                    + TEXT_GAP
                    + mc.font.width(formatRemaining(entry.remainingMillis()));
            maxWidth = Math.max(maxWidth, lineWidth);
        }
        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight(int count) {
        if (count <= 0) {
            return EMPTY_HEIGHT;
        }

        int lineHeight = Minecraft.getInstance().font.lineHeight;
        int titleBlock = WidgetUtils.showWidgetTitles() ? lineHeight + ROW_GAP : 0;
        return PADDING_Y * 2
                + titleBlock
                + count * lineHeight
                + Math.max(0, count - 1) * ROW_GAP;
    }

    static String formatRemaining(long remainingMillis) {
        long clamped = Math.max(0L, remainingMillis);
        if (clamped < 10_000L) {
            return String.format(Locale.ROOT, "%.1fs", clamped / 1000.0D);
        }
        return ((clamped + 999L) / 1000L) + "s";
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0F);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.drawString(mc.font, "Active Effects", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No active effects", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }
}
