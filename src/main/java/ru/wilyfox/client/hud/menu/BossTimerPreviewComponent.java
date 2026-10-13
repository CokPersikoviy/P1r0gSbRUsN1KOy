package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.widget.BossHudWidget;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

public final class BossTimerPreviewComponent extends SettingsComponent {
    private BossHudWidget.Preview preview;
    public BossTimerPreviewComponent() { super(0, 0, 0, 0, "Boss rows preview"); preferredHeight = 94; }
    private BossHudWidget.Preview preview() { if (preview == null) preview = new BossHudWidget.Preview(); return preview; }
    @Override public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        HudSurface.fillRounded(graphics, x, y, width, height, 4, WidgetTheme.PANEL_BG_SOFT);
        graphics.text(Minecraft.getInstance().font, label, x + 8, y + 6, WidgetTheme.TEXT_MUTED, false);
        preview().render(graphics, x + 8, y + 25, Math.max(16, width - 16), height - 30);
    }
    @Override public String getTooltip(int mouseX, int mouseY) { return isHovered(mouseX, mouseY) ? preview().text() : null; }
    @Override public boolean mouseClicked(double x, double y, int button) { return false; }
}
