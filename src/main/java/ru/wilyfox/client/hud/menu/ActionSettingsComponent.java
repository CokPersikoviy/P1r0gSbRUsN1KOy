package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

public class ActionSettingsComponent extends SettingsComponent {
    private final Runnable action;

    public ActionSettingsComponent(String label, Runnable action) {
        super(0, 0, 0, 0, label);
        this.action = action;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        boolean hovered = isHovered(mouseX, mouseY);

        HudSurface.fillRounded(context, x, y, width, height, 4, hovered ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT);
        if (hovered) {
            context.fill(x, y, x + width, y + 1, WidgetTheme.ACCENT_LINE);
        }

        int textY = y + (height - mc.font.lineHeight) / 2;
        context.drawCenteredString(mc.font, label, x + width / 2, textY, hovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_SOFT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) {
            return false;
        }

        action.run();
        return true;
    }
}
