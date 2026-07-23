package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.ThemePreset;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

public final class ThemePresetSettingsComponent extends SettingsComponent {
    private static final int SWAP_BUTTON_SIZE = 16;
    private static final int COLOR_PREVIEW_SIZE = 14;
    private static final int CONTROL_GAP = 5;

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
        int swapX = x + 8;
        int swapY = y + (height - SWAP_BUTTON_SIZE) / 2;
        int previewX = swapX + SWAP_BUTTON_SIZE + CONTROL_GAP;
        int previewY = y + (height - COLOR_PREVIEW_SIZE) / 2;
        boolean swapHovered = contains(mouseX, mouseY, swapX, swapY, SWAP_BUTTON_SIZE, SWAP_BUTTON_SIZE);
        PaletteColors colors = previewColors(selected);

        renderSwapButton(context, swapX, swapY, swapHovered, selected);
        renderColorPreview(context, previewX, previewY, colors, selected);

        context.drawString(mc.font, label, previewX + COLOR_PREVIEW_SIZE + 6, textY, textColor);

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

        int swapX = x + 8;
        int swapY = y + (height - SWAP_BUTTON_SIZE) / 2;
        if (contains(mouseX, mouseY, swapX, swapY, SWAP_BUTTON_SIZE, SWAP_BUTTON_SIZE)) {
            swapColors();
            ConfigManager.save();
            return true;
        }

        ConfigManager.get().theme.preset = preset;
        ConfigManager.get().theme.swapPresetColors = false;
        ConfigManager.save();
        return true;
    }

    private PaletteColors previewColors(boolean selected) {
        int primary;
        int secondary;
        if (preset == ThemePreset.CUSTOM) {
            primary = rgb(
                    ConfigManager.get().theme.customAccentRed,
                    ConfigManager.get().theme.customAccentGreen,
                    ConfigManager.get().theme.customAccentBlue
            );
            secondary = rgb(
                    ConfigManager.get().theme.customSecondaryRed,
                    ConfigManager.get().theme.customSecondaryGreen,
                    ConfigManager.get().theme.customSecondaryBlue
            );
        } else {
            primary = preset.getPrimaryRgb();
            secondary = preset.getSecondaryRgb();
            if (selected && ConfigManager.get().theme.swapPresetColors) {
                int originalPrimary = primary;
                primary = secondary;
                secondary = originalPrimary;
            }
        }
        return new PaletteColors(primary, secondary);
    }

    private void swapColors() {
        boolean selected = ConfigManager.get().theme.preset == preset;
        ConfigManager.get().theme.preset = preset;

        if (preset == ThemePreset.CUSTOM) {
            int primary = rgb(
                    ConfigManager.get().theme.customAccentRed,
                    ConfigManager.get().theme.customAccentGreen,
                    ConfigManager.get().theme.customAccentBlue
            );
            int secondary = rgb(
                    ConfigManager.get().theme.customSecondaryRed,
                    ConfigManager.get().theme.customSecondaryGreen,
                    ConfigManager.get().theme.customSecondaryBlue
            );
            setCustomPrimary(secondary);
            setCustomSecondary(primary);
            ConfigManager.get().theme.swapPresetColors = false;
            return;
        }

        ConfigManager.get().theme.swapPresetColors =
                selected ? !ConfigManager.get().theme.swapPresetColors : true;
    }

    private static void renderSwapButton(
            GuiGraphics context,
            int buttonX,
            int buttonY,
            boolean hovered,
            boolean selected
    ) {
        HudSurface.fillRounded(
                context,
                buttonX,
                buttonY,
                SWAP_BUTTON_SIZE,
                SWAP_BUTTON_SIZE,
                3,
                hovered || selected ? WidgetTheme.PANEL_BG : WidgetTheme.BAR_BG
        );

        int color = hovered || selected ? WidgetTheme.TITLE : WidgetTheme.TEXT_SECONDARY;
        context.fill(buttonX + 4, buttonY + 4, buttonX + 11, buttonY + 5, color);
        context.fill(buttonX + 10, buttonY + 4, buttonX + 11, buttonY + 8, color);
        context.fill(buttonX + 9, buttonY + 6, buttonX + 12, buttonY + 7, color);
        context.fill(buttonX + 10, buttonY + 7, buttonX + 11, buttonY + 8, color);

        context.fill(buttonX + 5, buttonY + 11, buttonX + 12, buttonY + 12, color);
        context.fill(buttonX + 5, buttonY + 8, buttonX + 6, buttonY + 12, color);
        context.fill(buttonX + 4, buttonY + 9, buttonX + 7, buttonY + 10, color);
        context.fill(buttonX + 5, buttonY + 8, buttonX + 6, buttonY + 9, color);
    }

    private static void renderColorPreview(
            GuiGraphics context,
            int previewX,
            int previewY,
            PaletteColors colors,
            boolean selected
    ) {
        int borderColor = selected ? WidgetTheme.TITLE : WidgetTheme.TEXT_MUTED;
        context.fill(
                previewX,
                previewY,
                previewX + COLOR_PREVIEW_SIZE,
                previewY + COLOR_PREVIEW_SIZE,
                borderColor
        );

        int innerX = previewX + 1;
        int innerY = previewY + 1;
        int innerSize = COLOR_PREVIEW_SIZE - 2;
        context.fill(
                innerX,
                innerY,
                innerX + innerSize,
                innerY + innerSize,
                0xFF000000 | colors.secondary()
        );
        int diagonalColor = 0xFF000000 | mix(colors.primary(), colors.secondary());
        for (int row = 0; row < innerSize; row++) {
            int diagonalX = innerX + innerSize - row - 1;
            if (diagonalX > innerX) {
                context.fill(
                        innerX,
                        innerY + row,
                        diagonalX,
                        innerY + row + 1,
                        0xFF000000 | colors.primary()
                );
            }
            context.fill(diagonalX, innerY + row, diagonalX + 1, innerY + row + 1, diagonalColor);
        }
    }

    private static boolean contains(
            double mouseX,
            double mouseY,
            int left,
            int top,
            int width,
            int height
    ) {
        return mouseX >= left
                && mouseX < left + width
                && mouseY >= top
                && mouseY < top + height;
    }

    private static int rgb(int red, int green, int blue) {
        return (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }

    private static int mix(int first, int second) {
        int red = (((first >> 16) & 0xFF) + ((second >> 16) & 0xFF)) / 2;
        int green = (((first >> 8) & 0xFF) + ((second >> 8) & 0xFF)) / 2;
        int blue = ((first & 0xFF) + (second & 0xFF)) / 2;
        return rgb(red, green, blue);
    }

    private static void setCustomPrimary(int color) {
        ConfigManager.get().theme.customAccentRed = color >> 16 & 0xFF;
        ConfigManager.get().theme.customAccentGreen = color >> 8 & 0xFF;
        ConfigManager.get().theme.customAccentBlue = color & 0xFF;
    }

    private static void setCustomSecondary(int color) {
        ConfigManager.get().theme.customSecondaryRed = color >> 16 & 0xFF;
        ConfigManager.get().theme.customSecondaryGreen = color >> 8 & 0xFF;
        ConfigManager.get().theme.customSecondaryBlue = color & 0xFF;
    }

    private record PaletteColors(int primary, int secondary) {
    }
}
