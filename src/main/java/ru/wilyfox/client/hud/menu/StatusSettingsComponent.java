package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.function.Supplier;

public class StatusSettingsComponent extends SettingsComponent {
    private final Supplier<String> valueSupplier;

    public StatusSettingsComponent(String label, Supplier<String> valueSupplier) {
        super(0, 0, 0, 0, label);
        this.valueSupplier = valueSupplier;
        this.preferredHeight = 30;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        String value = valueSupplier.get();
        if (value == null || value.isBlank()) {
            value = "-";
        }

        HudSurface.fillRounded(context, x, y, width, height, 4, WidgetTheme.PANEL_BG_SOFT);
        context.drawString(mc.font, label, x + 8, y + 6, WidgetTheme.TEXT_MUTED, false);
        context.drawString(mc.font, value, x + 8, y + height - mc.font.lineHeight - 6, getStatusColor(value), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    private static int getStatusColor(String value) {
        String normalized = value.toLowerCase();
        if (normalized.contains("running") || normalized.contains("connected")) {
            return WidgetTheme.STATUS_SUCCESS;
        }
        if (normalized.contains("retry") || normalized.contains("busy") || normalized.contains("connecting")) {
            return WidgetTheme.STATUS_WARNING;
        }
        if (normalized.contains("invalid") || normalized.contains("error") || normalized.contains("failed") || normalized.contains("empty")) {
            return WidgetTheme.STATUS_ERROR;
        }
        return WidgetTheme.STATUS_INFO;
    }
}
