package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class CycleSettingsComponent<T> extends SettingsComponent {
    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final T[] values;
    private final Function<T, String> labelMapper;

    public CycleSettingsComponent(int x, int y, int width, int height, String label,
                                  Supplier<T> getter,
                                  Consumer<T> setter,
                                  T[] values,
                                  Function<T, String> labelMapper) {
        super(x, y, width, height, label);
        this.getter = getter;
        this.setter = setter;
        this.values = values;
        this.labelMapper = labelMapper;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();

        boolean hovered = isHovered(mouseX, mouseY);
        int rowBg = hovered ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT;
        int textColor = hovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY;

        HudSurface.fillRounded(context, x, y, width, height, 4, rowBg);

        int textY = y + (height - mc.font.lineHeight) / 2;
        context.drawString(mc.font, label, x + 8, textY, textColor);

        String valueText = labelMapper.apply(getter.get());
        int maxValueWidth = Math.min(128, width / 2);
        int valueWidth = Math.max(52, Math.min(maxValueWidth, mc.font.width(valueText) + 16));
        int valueX = x + width - 8 - valueWidth;

        HudSurface.fillRounded(context, valueX, y + 3, valueWidth, height - 6, 3, hovered ? WidgetTheme.PANEL_BG : WidgetTheme.BAR_BG);
        context.drawCenteredString(mc.font, valueText, valueX + valueWidth / 2, textY, WidgetTheme.TITLE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) {
            return false;
        }

        T current = getter.get();
        int index = 0;

        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                index = i;
                break;
            }
        }

        setter.accept(values[(index + 1) % values.length]);
        ConfigManager.save();
        return true;
    }
}
