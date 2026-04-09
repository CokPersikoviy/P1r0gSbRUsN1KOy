package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.ability.AbilityCooldownStore;
import ru.wilyfox.client.ability.AbilityCooldownStore.Entry;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
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

    public AbilityCooldownWidget(int x, int y, HudLayer layer, AbilityCooldownStore store) {
        super(x, y, layer);
        this.store = store;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<Entry> entries = store.getActiveEntries();

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

        context.fill(0, 0, width, height, WidgetTheme.WIDGET_PANEL_BG);
        context.fill(0, 0, width, 1, WidgetTheme.WIDGET_ACCENT_LINE);

        int y = PADDING_Y;
        context.drawString(mc.font, "Ability Cooldowns", PADDING_X, y, WidgetTheme.TITLE);
        y += mc.font.lineHeight + 3;

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
        return Math.round(getUnscaledWidth(store.getActiveEntries()) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight(store.getActiveEntries().size()) * getScale());
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
        int maxWidth = mc.font.width("Ability Cooldowns");

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
        return PADDING_Y * 2 + Minecraft.getInstance().font.lineHeight + 3 + count * rowHeight + Math.max(0, count - 1) * ROW_GAP;
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

        context.fill(0, 0, EMPTY_WIDTH, EMPTY_HEIGHT, WidgetTheme.WIDGET_PANEL_BG_SOFT);
        context.fill(0, 0, EMPTY_WIDTH, 1, WidgetTheme.WIDGET_ACCENT_LINE);
        context.drawString(mc.font, "Ability Cooldowns", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No active abilities", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }
}

