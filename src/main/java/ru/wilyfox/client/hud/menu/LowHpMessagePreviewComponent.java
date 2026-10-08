package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.chat.LowHpMessageFormatter;
import ru.wilyfox.client.hud.config.LowHpMessageFormatConfig;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.function.Supplier;

public final class LowHpMessagePreviewComponent extends SettingsComponent {
    private final Supplier<LowHpMessageFormatConfig> getter;

    public LowHpMessagePreviewComponent(Supplier<LowHpMessageFormatConfig> getter) {
        super(0, 0, 0, 0, "Message preview");
        this.getter = getter;
        preferredHeight = 68;
    }

    @Override public void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        var message = LowHpMessageFormatter.preview(getter.get());
        HudSurface.fillRounded(context, x, y, width, height, 4, WidgetTheme.PANEL_BG_SOFT);
        context.text(font, label, x + 8, y + 6, WidgetTheme.TEXT_MUTED, false);
        if (message.isEmpty()) {
            context.text(font, "Enable at least one element", x + 8, y + 23, WidgetTheme.STATUS_WARNING, false);
            return;
        }
        int lineY = y + 23;
        for (var line : font.split(message.component(), Math.max(16, width - 16))) {
            if (lineY + font.lineHeight > y + height - 5) break;
            context.text(font, line, x + 8, lineY, WidgetTheme.TEXT_PRIMARY, false);
            lineY += font.lineHeight + 2;
        }
    }

    @Override public String getTooltip(int mouseX, int mouseY) {
        return isHovered(mouseX, mouseY) ? LowHpMessageFormatter.preview(getter.get()).plainText() : null;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
}
