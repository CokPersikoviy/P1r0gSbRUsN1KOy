package ru.wilyfox.client.hud.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.chat.ChatTextFilter;
import ru.wilyfox.client.hud.config.ChatWidgetConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import java.util.function.Supplier;

/** Syntax feedback stays beside the editable field, including while a pattern is incomplete. */
final class ChatFilterSettingsComponent extends TextInputSettingsComponent {
    private final Supplier<ChatWidgetConfig> config;
    private ChatTextFilter compiled = ChatTextFilter.EMPTY;
    ChatFilterSettingsComponent(Supplier<ChatWidgetConfig> config) {
        super(0, 0, 0, 0, "Text filter", () -> config.get().textFilter,
                value -> { config.get().textFilter = value; ConfigManager.layoutChanged(); }, ChatWidgetConfig.MAX_FILTER_LENGTH);
        this.config = config;
    }
    private ChatTextFilter filter() {
        var value = config.get();
        if (!compiled.same(value.textFilter, value.regexFilter)) compiled = ChatTextFilter.compile(value.textFilter, value.regexFilter);
        return compiled;
    }
    @Override public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.render(graphics, mouseX, mouseY);
        if (filter().error() != null) {
            int boxWidth = Math.max(120, width / 2), left = x + width - 8 - boxWidth;
            graphics.fill(left + 3, y + 3, left + boxWidth - 3, y + 4, WidgetTheme.STATUS_ERROR);
        }
    }
    @Override public String getTooltip(int mouseX, int mouseY) {
        if (!isHovered(mouseX, mouseY)) return null;
        String error = filter().error();
        return error != null ? Component.translatable("froghelper.chat.filter_invalid", error).getString()
                : Component.translatable(config.get().regexFilter ? "froghelper.chat.filter_regex_help"
                        : "froghelper.chat.filter_text_help").getString();
    }
}
