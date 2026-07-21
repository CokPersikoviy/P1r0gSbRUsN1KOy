package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ToggleSettingsComponent extends SettingsComponent {
    private final Supplier<Boolean> getter;
    private final Consumer<Boolean> setter;
    private String warningTooltip;

    protected ToggleSettingsComponent(int x, int y, int width, int height, String label,
                                      Supplier<Boolean> g,
                                      Consumer<Boolean> s) {
        super(x, y, width, height, label);
        this.getter = g;
        this.setter = s;
    }

    public ToggleSettingsComponent withWarningTooltip(String warningTooltip) {
        this.warningTooltip = warningTooltip;
        return this;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();

        boolean hovered = isHovered(mouseX, mouseY);
        boolean value = getter.get();

        int rowBg = hovered ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT;
        int textColor = hovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY;

        HudSurface.fillRounded(context, x, y, width, height, 4, rowBg);

        int textY = y + (height - mc.font.lineHeight) / 2;
        context.drawString(mc.font, label, x + 8, textY, textColor);
        renderWarning(context, mc, mouseX, mouseY, textY);

        String stateText = value ? "ON" : "OFF";
        int pillWidth = 30;
        int pillHeight = 12;
        int pillX = x + width - 8 - pillWidth;
        int pillY = y + (height - pillHeight) / 2;

        int pillBg;
        int pillTextColor;

        if (value) {
            pillBg = hovered ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT;
            pillTextColor = WidgetTheme.TITLE;
        } else {
            pillBg = hovered ? WidgetTheme.BAR_BG : WidgetTheme.PANEL_BG_SOFT;
            pillTextColor = WidgetTheme.TEXT_SECONDARY;
        }

        HudSurface.fillRounded(context, pillX, pillY, pillWidth, pillHeight, 3, pillBg);
        if (value) {
            context.fill(pillX + 3, pillY, pillX + pillWidth - 3, pillY + 1, WidgetTheme.ACCENT_LINE);
        }
        context.drawCenteredString(mc.font, stateText, pillX + pillWidth / 2, pillY + 2, pillTextColor);
    }

    private void renderWarning(GuiGraphics context, Minecraft mc, int mouseX, int mouseY, int textY) {
        if (warningTooltip == null || warningTooltip.isBlank()) {
            return;
        }

        int iconX = x + 8 + mc.font.width(label) + 6;
        int iconSize = 9;
        int iconY = y + (height - iconSize) / 2;
        int left = iconX;
        int right = iconX + iconSize;
        int top = iconY;
        int bottom = iconY + iconSize;

        context.fill(left + iconSize / 2, top, right, bottom, WidgetTheme.STATUS_WARNING);
        context.fill(left, bottom, left + iconSize / 2, top, WidgetTheme.STATUS_WARNING);
        context.drawCenteredString(mc.font, "!", left + iconSize / 2, textY, 0xFF1A1A1A);

        boolean warningHovered = mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
        if (warningHovered) {
            context.renderTooltip(mc.font, Component.literal(warningTooltip), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) {
            return false;
        }

        setter.accept(!getter.get());
        ConfigManager.save();
        return true;
    }
}
