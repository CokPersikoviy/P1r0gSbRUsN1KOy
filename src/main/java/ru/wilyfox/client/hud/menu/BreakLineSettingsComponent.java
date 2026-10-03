package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.widget.WidgetTheme;

public class BreakLineSettingsComponent extends SettingsComponent {
    public BreakLineSettingsComponent(String label) {
        super(0, 0, 0, 0, label);
        this.preferredHeight = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int centerY = y + height / 2;
        int labelWidth = label == null || label.isBlank() ? 0 : mc.font.width(label);
        int gap = labelWidth > 0 ? 10 : 0;
        int halfWidth = (width - labelWidth - gap * 2) / 2;
        int leftStart = x + 4;
        int leftEnd = x + Math.max(4, halfWidth);
        int rightStart = x + width - Math.max(4, halfWidth);
        int rightEnd = x + width - 4;

        if (leftEnd > leftStart) {
            context.fill(leftStart, centerY, leftEnd, centerY + 1, WidgetTheme.BAR_BG);
        }
        if (rightEnd > rightStart) {
            context.fill(rightStart, centerY, rightEnd, centerY + 1, WidgetTheme.BAR_BG);
        }

        if (labelWidth > 0) {
            int textX = x + (width - labelWidth) / 2;
            int textY = y + (height - mc.font.lineHeight) / 2;
            context.text(mc.font, label, textX, textY, WidgetTheme.TEXT_SECONDARY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}
