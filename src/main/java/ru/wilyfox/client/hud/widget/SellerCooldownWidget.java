package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.SellerCooldownFilter;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.seller.SellerCooldownStore;

import java.util.List;
import java.util.Locale;

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
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<SellerCooldownStore.Entry> entries = getVisibleEntries();

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

        context.pose().pushMatrix();
        context.pose().translate(startX, startY);
        context.pose().scale(scale, scale);

        HudSurface.drawPanel(context, width, height);

        if (WidgetUtils.showWidgetTitles()) {
            context.text(mc.font, "Sellers", PADDING_X, y, WidgetTheme.TITLE);
            y += mc.font.lineHeight + 3;
        }

        for (SellerCooldownStore.Entry entry : entries) {
            boolean ready = entry.ready();
            String state = ready ? "Ready" : formatRemaining(entry.remainingMillis());
            int stateColor = ready ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SECONDARY;
            int stateWidth = mc.font.width(state);

            context.text(mc.font, entry.name() + ":", PADDING_X, y, WidgetTheme.TEXT_SOFT);
            context.text(mc.font, state, width - PADDING_X - stateWidth, y, stateColor);
            y += mc.font.lineHeight + LINE_GAP;
        }

        context.pose().popMatrix();
    }

    @Override
    public int getWidth() {
        return Math.round(getUnscaledWidth(Minecraft.getInstance(), getVisibleEntries()) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight(Minecraft.getInstance(), getVisibleEntries().size()) * getScale());
    }

    @Override
    public boolean isVisible() {
        return isInLayout() && (!getVisibleEntries().isEmpty() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Seller Cooldowns";
    }

    private int getUnscaledWidth(Minecraft mc, List<SellerCooldownStore.Entry> entries) {
        if (entries.isEmpty()) {
            return EMPTY_WIDTH;
        }

        int maxWidth = WidgetUtils.showWidgetTitles() ? mc.font.width("Sellers") : 0;
        for (SellerCooldownStore.Entry entry : entries) {
            String state = entry.ready() ? "Ready" : formatRemaining(entry.remainingMillis());
            maxWidth = Math.max(maxWidth, mc.font.width(entry.name() + ": " + state));
        }
        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight(Minecraft mc, int count) {
        if (count <= 0) {
            return EMPTY_HEIGHT;
        }

        int titleBlock = WidgetUtils.showWidgetTitles() ? mc.font.lineHeight + 3 : 0;
        return PADDING_Y * 2 + titleBlock + count * (mc.font.lineHeight + LINE_GAP);
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().gui.screen() instanceof HudEditingScreen;
    }

    private List<SellerCooldownStore.Entry> getVisibleEntries() {
        SellerCooldownFilter filter = ConfigManager.get().sellerCooldown.filter;
        if (filter == null) {
            filter = SellerCooldownFilter.ALL;
        }
        SellerCooldownFilter selectedFilter = filter;
        return store.getEntries().stream()
                .filter(entry -> selectedFilter.matches(entry.ready()))
                .toList();
    }

    private static String formatRemaining(long remainingMillis) {
        long totalSeconds = Math.max(0L, remainingMillis) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        return hours > 0L
                ? String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private void renderPlaceholder(GuiGraphicsExtractor context, Minecraft mc) {
        context.pose().pushMatrix();
        context.pose().translate(startX, startY);
        context.pose().scale(scale, scale);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.text(mc.font, "Sellers", PADDING_X, 6, WidgetTheme.TITLE);
        context.text(mc.font, "No sellers", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popMatrix();
    }
}
