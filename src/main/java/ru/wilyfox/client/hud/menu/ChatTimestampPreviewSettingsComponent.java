package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.chat.ChatTimestampFormatter;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

final class ChatTimestampPreviewSettingsComponent extends SettingsComponent {
    ChatTimestampPreviewSettingsComponent() { super(0, 0, 0, 30, "Timestamp preview"); preferredHeight = 30; }
    @Override public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        HudSurface.fillRounded(graphics, x, y, width, height, 4, WidgetTheme.PANEL_BG_SOFT);
        graphics.text(Minecraft.getInstance().font, net.minecraft.network.chat.Component.empty()
                        .append(ChatTimestampFormatter.createTimestampPrefix())
                        .append(net.minecraft.network.chat.Component.literal("Fox: Hello").withColor(WidgetTheme.TEXT_PRIMARY)),
                x + 8, y + (height - Minecraft.getInstance().font.lineHeight) / 2, WidgetTheme.TEXT_PRIMARY);
    }
    @Override public boolean mouseClicked(double x, double y, int button) { return false; }
}
