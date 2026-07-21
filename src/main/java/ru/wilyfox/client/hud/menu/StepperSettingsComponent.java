package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class StepperSettingsComponent extends SettingsComponent {
    private final IntSupplier getter;
    private final IntConsumer setter;
    private final int min;
    private final IntSupplier max;
    private final int step;

    public StepperSettingsComponent(int x, int y, int width, int height, String label,
                                    IntSupplier getter,
                                    IntConsumer setter,
                                    int min, int max, int step) {
        this(x, y, width, height, label, getter, setter, min, () -> max, step);
    }

    public StepperSettingsComponent(int x, int y, int width, int height, String label,
                                    IntSupplier getter,
                                    IntConsumer setter,
                                    int min, IntSupplier max, int step) {
        super(x, y, width, height, label);
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();

        boolean hovered = isHovered(mouseX, mouseY);

        int rowBg = hovered ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT;
        int textColor = hovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY;
        int valueColor = WidgetTheme.TEXT_SOFT;

        HudSurface.fillRounded(context, x, y, width, height, 4, rowBg);

        int textY = y + (height - mc.font.lineHeight) / 2;
        context.drawString(mc.font, label, x + 8, textY, textColor);

        int buttonWidth = 16;
        int buttonHeight = height - 6;
        int buttonY = y + 3;

        int plusX = x + width - 8 - buttonWidth;
        int valueWidth = 30;
        int valueX = plusX - valueWidth - 4;
        int minusX = valueX - 4 - buttonWidth;

        boolean hoverMinus = mouseX >= minusX && mouseX <= minusX + buttonWidth
                && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        boolean hoverPlus = mouseX >= plusX && mouseX <= plusX + buttonWidth
                && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;

        int buttonBg = WidgetTheme.BAR_BG;
        int buttonHoverBg = WidgetTheme.PANEL_BG;

        HudSurface.fillRounded(context, minusX, buttonY, buttonWidth, buttonHeight, 3, hoverMinus ? buttonHoverBg : buttonBg);
        HudSurface.fillRounded(context, plusX, buttonY, buttonWidth, buttonHeight, 3, hoverPlus ? buttonHoverBg : buttonBg);

        context.drawCenteredString(mc.font, "-", minusX + buttonWidth / 2, textY, WidgetTheme.TITLE);
        context.drawCenteredString(mc.font, "+", plusX + buttonWidth / 2, textY, WidgetTheme.TITLE);

        String valueText = String.valueOf(getter.getAsInt());
        context.drawCenteredString(mc.font, valueText, valueX + valueWidth / 2, textY, valueColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) {
            return false;
        }

        int buttonWidth = 16;
        int buttonHeight = height - 6;
        int buttonY = y + 3;

        int plusX = x + width - 8 - buttonWidth;
        int valueWidth = 30;
        int valueX = plusX - valueWidth - 4;
        int minusX = valueX - 4 - buttonWidth;

        if (mouseX >= minusX && mouseX <= minusX + buttonWidth
                && mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
            setter.accept(Math.max(min, getter.getAsInt() - step));
            ConfigManager.save();
            return true;
        }

        if (mouseX >= plusX && mouseX <= plusX + buttonWidth
                && mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
            setter.accept(Math.min(max.getAsInt(), getter.getAsInt() + step));
            ConfigManager.save();
            return true;
        }

        return true;
    }
}
