package ru.wilyfox.client.chat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.widget.WidgetTheme;

public enum ChatInputMode {
    CHAT("CHAT"), FH("FH"), REGEX("REGEX");
    private final String label;
    ChatInputMode(String label) { this.label = label; }
    public int width(Font font) { return font.width(label) + 10; }
    public static ChatInputMode current(boolean searching) {
        return searching ? REGEX : ChatDock.outgoingChannel() == ChatTab.FH ? FH : CHAT;
    }
    public void render(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        graphics.fill(x, y, x + width(font), y + 12, WidgetTheme.PANEL_BG);
        graphics.fill(x, y + 11, x + width(font), y + 12, WidgetTheme.ACCENT_LINE);
        graphics.text(font, label, x + 5, y + 2, WidgetTheme.TITLE);
    }
}
