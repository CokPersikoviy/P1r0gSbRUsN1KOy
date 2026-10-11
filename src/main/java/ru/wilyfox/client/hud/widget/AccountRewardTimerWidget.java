package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.resource.ResourceTimerStore;
import java.util.function.Supplier;
import java.util.Locale;

abstract class AccountRewardTimerWidget extends AbstractWidget {
    private long cachedSecond = Long.MIN_VALUE;
    private long cachedDeadline = Long.MIN_VALUE;
    private String text = "No saved timer";
    private final String title, displayName;
    private final Supplier<ResourceTimerStore.Entry> timer;
    protected AccountRewardTimerWidget(int x, int y, HudLayer layer, String title, String displayName, Supplier<ResourceTimerStore.Entry> timer) {
        super(x, y, layer); this.title = title; this.displayName = displayName; this.timer = timer;
    }
    private void measure() {
        var entry = timer.get();
        long now = System.currentTimeMillis(), deadline = entry == null ? Long.MIN_VALUE : entry.endsAt();
        if (cachedSecond != now / 1000 || cachedDeadline != deadline) {
            cachedSecond = now / 1000; cachedDeadline = deadline;
            long seconds = entry == null ? 0 : Math.max(0, (deadline - now + 999) / 1000);
            text = entry == null ? "No saved timer" : seconds == 0 ? "Ready" : String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        }
        var font = Minecraft.getInstance().font;
        width = 12 + Math.max(font.width(text), WidgetUtils.showWidgetTitles() ? font.width(title) : 0);
        height = 10 + font.lineHeight + (WidgetUtils.showWidgetTitles() ? font.lineHeight + 2 : 0);
    }
    @Override public int getWidth() { measure(); return Math.round(width * scale); }
    @Override public int getHeight() { measure(); return Math.round(height * scale); }
    @Override public boolean isVisible() {
        return isInLayout() && (timer.get() != null || Minecraft.getInstance().gui.screen() instanceof HudEditingScreen);
    }
    @Override public String getDisplayName() { return displayName; }
    @Override public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (!isVisible()) return;
        measure(); var font = Minecraft.getInstance().font;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(startX, startY).scale(scale, scale);
            HudSurface.drawPanel(graphics, width, height);
            int y = 5;
            if (WidgetUtils.showWidgetTitles()) { graphics.text(font, title, 6, y, WidgetTheme.TITLE); y += font.lineHeight + 2; }
            graphics.text(font, text, 6, y, "Ready".equals(text) ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SOFT);
        } finally { graphics.pose().popMatrix(); }
    }
}
