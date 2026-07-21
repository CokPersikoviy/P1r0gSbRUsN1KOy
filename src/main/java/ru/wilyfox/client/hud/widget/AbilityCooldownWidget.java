package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.ability.AbilityCooldownStore;
import ru.wilyfox.client.ability.AbilityCooldownStore.Entry;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.internal.HudFrameClock;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.utils.Formatting;

import java.util.List;

public class AbilityCooldownWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int LINE_GAP = 2;
    private static final int ROW_GAP = 3;
    private static final int BAR_HEIGHT = 2;
    private static final int EMPTY_WIDTH = 116;
    private static final int EMPTY_HEIGHT = 28;

    private final AbilityCooldownStore store;

    // Per-frame cache: render()/getWidth()/getHeight() each call getActiveEntries() (which rebuilds a
    // list) every frame; compute once per HUD frame, keyed on HudFrameClock.
    private long cachedFrameId = Long.MIN_VALUE;
    private List<Entry> cachedEntries;

    public AbilityCooldownWidget(int x, int y, HudLayer layer, AbilityCooldownStore store) {
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
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context, mc);
            return;
        }

        int width = getUnscaledWidth(entries);
        int height = getUnscaledHeight(entries.size());
        int rowHeight = mc.font.lineHeight + BAR_HEIGHT + LINE_GAP;

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPanel(context, width, height);

        int y = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.drawString(mc.font, "Ability Cooldowns", PADDING_X, y, WidgetTheme.TITLE);
            y += mc.font.lineHeight + 3;
        }

        for (Entry entry : entries) {
            String remaining = formatSeconds(entry.remainingMillis());
            int timeWidth = mc.font.width(remaining);
            int barTop = y + mc.font.lineHeight + 1;
            int barRight = width - PADDING_X;

            context.drawString(mc.font, entry.name(), PADDING_X, y, WidgetTheme.TEXT_SOFT);
            context.drawString(mc.font, remaining, barRight - timeWidth, y, WidgetTheme.TEXT_SECONDARY);

            context.fill(PADDING_X, barTop, barRight, barTop + BAR_HEIGHT, WidgetTheme.BAR_BG);

            int innerWidth = barRight - PADDING_X;
            int fillWidth = Math.max(0, Math.min(innerWidth, Math.round(innerWidth * entry.progress())));
            if (fillWidth > 0) {
                context.fill(PADDING_X, barTop, PADDING_X + fillWidth, barTop + BAR_HEIGHT, WidgetTheme.BAR_FILL);
            }

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
        return ConfigManager.get().abilityCooldown.active && (store.hasActiveEntries() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Ability Cooldowns";
    }

    private int getUnscaledWidth(List<Entry> entries) {
        if (entries.isEmpty()) {
            return EMPTY_WIDTH;
        }

        Minecraft mc = Minecraft.getInstance();
        int maxWidth = WidgetUtils.showWidgetTitles() ? mc.font.width("Ability Cooldowns") : 0;

        for (Entry entry : entries) {
            String line = entry.name() + " " + formatSeconds(entry.remainingMillis());
            maxWidth = Math.max(maxWidth, mc.font.width(line));
        }

        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight(int count) {
        if (count <= 0) {
            return EMPTY_HEIGHT;
        }

        int rowHeight = Minecraft.getInstance().font.lineHeight + BAR_HEIGHT + LINE_GAP;
        int titleBlock = WidgetUtils.showWidgetTitles() ? Minecraft.getInstance().font.lineHeight + 3 : 0;
        return PADDING_Y * 2 + titleBlock + count * rowHeight + Math.max(0, count - 1) * ROW_GAP;
    }

    private String formatSeconds(long remainingMillis) {
        return Formatting.formatMillis(System.currentTimeMillis() + remainingMillis);
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.drawString(mc.font, "Ability Cooldowns", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No active abilities", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }
}

