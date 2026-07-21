package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

public class SlotHeaderSettingsComponent extends SettingsComponent {
    private final java.util.function.BooleanSupplier expandedGetter;
    private final Runnable toggleAction;

    public SlotHeaderSettingsComponent(String label,
                                       java.util.function.BooleanSupplier expandedGetter,
                                       Runnable toggleAction) {
        super(0, 0, 0, 0, label);
        this.expandedGetter = expandedGetter;
        this.toggleAction = toggleAction;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        boolean hovered = isHovered(mouseX, mouseY);
        boolean expanded = expandedGetter.getAsBoolean();

        HudSurface.fillRounded(context, x, y, width, height, 4, hovered ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT);

        String arrow = expanded ? "v" : ">";
        int textY = y + (height - mc.font.lineHeight) / 2;
        context.drawString(mc.font, arrow, x + 8, textY, hovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_SOFT);
        context.drawString(mc.font, label, x + 20, textY, hovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) {
            return false;
        }

        toggleAction.run();
        return true;
    }
}
