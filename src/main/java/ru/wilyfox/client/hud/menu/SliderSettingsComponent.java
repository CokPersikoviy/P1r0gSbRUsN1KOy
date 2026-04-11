package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class SliderSettingsComponent extends SettingsComponent{
    private final IntSupplier getter;
    private final IntConsumer setter;
    private final int min;
    private final int max;
    private boolean dragging = false;
    private boolean dirty = false;

    public SliderSettingsComponent(int x, int y, int width, int height, String label,
                                   IntSupplier getter,
                                   IntConsumer setter,
                                   int min, int max) {
        super(x, y, width, height, label);
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();

        boolean hovered = isHovered(mouseX, mouseY);

        int rowBg = hovered ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT;
        int textColor = hovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY;
        int valueColor = WidgetTheme.TEXT_SOFT;

        context.fill(x, y, x + width, y + height, rowBg);

        int textY = y + (height - mc.font.lineHeight) / 2;
        context.drawString(mc.font, label, x + 8, textY, textColor);

        int valueWidth = 24;
        int valueX = x + width - 8 - valueWidth;

        String valueText = String.valueOf(getter.getAsInt());
        context.drawCenteredString(mc.font, valueText, valueX + valueWidth / 2, textY, valueColor);

        int sliderX = x + 92;
        int sliderWidth = valueX - sliderX - 10;
        int sliderY = y + height / 2 - 1;

        context.fill(sliderX, sliderY, sliderX + sliderWidth, sliderY + 2, WidgetTheme.BAR_BG);

        double t = (getter.getAsInt() - min) / (double) (max - min);
        int fillWidth = (int) Math.round(sliderWidth * t);
        int knobX = sliderX + fillWidth;

        context.fill(sliderX, sliderY, sliderX + fillWidth, sliderY + 2, WidgetTheme.BAR_FILL);

        int knobColor = dragging ? WidgetTheme.TITLE : (hovered ? WidgetTheme.TEXT_SOFT : WidgetTheme.TEXT_PRIMARY);
        context.fill(knobX - 2, sliderY - 3, knobX + 2, sliderY + 5, knobColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) {
            return false;
        }

        dragging = true;
        updateValue(mouseX);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging || button != 0) {
            return false;
        }

        updateValue(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            saveIfDirty();
            return true;
        }

        return false;
    }

    private void updateValue(double mouseX) {
        int valueWidth = 24;
        int valueX = x + width - 8 - valueWidth;

        int sliderX = x + 92;
        int sliderWidth = valueX - sliderX - 10;

        double t = (mouseX - sliderX) / sliderWidth;
        t = Math.max(0.0, Math.min(1.0, t));

        int value = min + (int) Math.round((max - min) * t);
        if (value != getter.getAsInt()) {
            setter.accept(value);
            dirty = true;
        }
    }

    private void saveIfDirty() {
        if (!dirty) {
            return;
        }

        dirty = false;
        ConfigManager.save();
    }
}
