package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.ThemePreset;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

public final class ThemePresetSettingsComponent extends SettingsComponent {
    private final ThemePreset preset;

    public ThemePresetSettingsComponent(int x, int y, int width, int height, ThemePreset preset) {
        super(x, y, width, height, preset.getTitle());
        this.preset = preset;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        boolean hovered = isHovered(mouseX, mouseY);
        boolean selected = ConfigManager.get().theme.preset == preset;

        int rowBg = hovered ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT;
        int textColor = selected ? WidgetTheme.TITLE : (hovered ? WidgetTheme.TEXT_SOFT : WidgetTheme.TEXT_PRIMARY);
        int chipBg = selected ? WidgetTheme.PANEL_BG : WidgetTheme.BAR_BG;

        HudSurface.fillRounded(context, x, y, width, height, 4, rowBg);

        int textY = y + (height - mc.font.lineHeight) / 2;
        int squareSize = 10;
        int squareX = x + 8;
        int squareY = y + (height - squareSize) / 2;
        context.fill(squareX, squareY, squareX + squareSize, squareY + squareSize, 0xFF000000 | preset.getAccentRgb());

        context.drawString(mc.font, label, squareX + squareSize + 6, textY, textColor);

        String stateText = selected ? "Current" : "Apply";
        int chipWidth = 42;
        int chipX = x + width - 8 - chipWidth;
        context.fill(chipX, y + 3, chipX + chipWidth, y + height - 3, chipBg);
        if (selected) {
            context.fill(chipX, y + 3, chipX + chipWidth, y + 4, WidgetTheme.ACCENT_LINE);
        }
        context.drawCenteredString(mc.font, stateText, chipX + chipWidth / 2, textY, selected ? WidgetTheme.TITLE : WidgetTheme.TEXT_SECONDARY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) {
            return false;
        }

        ConfigManager.get().theme.preset = preset;
        ConfigManager.save();
        return true;
    }
}
