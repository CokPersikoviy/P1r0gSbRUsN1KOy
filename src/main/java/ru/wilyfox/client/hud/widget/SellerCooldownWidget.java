package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.seller.SellerCooldownStore;
import ru.wilyfox.utils.Formatting;

import java.util.List;

public final class SellerCooldownWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int LINE_GAP = 2;
    private static final int EMPTY_WIDTH = 114;
    private static final int EMPTY_HEIGHT = 28;

    private final SellerCooldownStore store;

    public SellerCooldownWidget(int x, int y, HudLayer layer, SellerCooldownStore store) {
        super(x, y, layer);
        this.store = store;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<SellerCooldownStore.Entry> entries = store.getEntries();

        if (entries.isEmpty()) {
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context, mc);
            return;
        }

        int width = getUnscaledWidth(mc, entries);
        int height = getUnscaledHeight(mc, entries.size());
        int y = PADDING_Y;

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        context.fill(0, 0, width, height, WidgetTheme.WIDGET_PANEL_BG);
        context.fill(0, 0, width, 1, WidgetTheme.WIDGET_ACCENT_LINE);

        context.drawString(mc.font, "Sellers", PADDING_X, y, WidgetTheme.TITLE);
        y += mc.font.lineHeight + 3;

        for (SellerCooldownStore.Entry entry : entries) {
            String state = entry.ready() ? "Ready" : Formatting.formatMillis(System.currentTimeMillis() + entry.remainingMillis());
            int stateColor = entry.ready() ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SECONDARY;
            int stateWidth = mc.font.width(state);

            context.drawString(mc.font, entry.name() + ":", PADDING_X, y, WidgetTheme.TEXT_SOFT);
            context.drawString(mc.font, state, width - PADDING_X - stateWidth, y, stateColor);
            y += mc.font.lineHeight + LINE_GAP;
        }

        context.pose().popPose();
    }

    @Override
    public int getWidth() {
        return Math.round(getUnscaledWidth(Minecraft.getInstance(), store.getEntries()) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight(Minecraft.getInstance(), store.getEntries().size()) * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().sellerCooldown.active && (store.hasEntries() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Seller Cooldowns";
    }

    private int getUnscaledWidth(Minecraft mc, List<SellerCooldownStore.Entry> entries) {
        if (entries.isEmpty()) {
            return EMPTY_WIDTH;
        }

        int maxWidth = mc.font.width("Sellers");
        for (SellerCooldownStore.Entry entry : entries) {
            String state = entry.ready() ? "Ready" : Formatting.formatMillis(System.currentTimeMillis() + entry.remainingMillis());
            maxWidth = Math.max(maxWidth, mc.font.width(entry.name() + ": " + state));
        }
        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight(Minecraft mc, int count) {
        if (count <= 0) {
            return EMPTY_HEIGHT;
        }

        return PADDING_Y * 2 + mc.font.lineHeight + 3 + count * (mc.font.lineHeight + LINE_GAP);
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
        context.drawString(mc.font, "Sellers", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No sellers", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }
}

