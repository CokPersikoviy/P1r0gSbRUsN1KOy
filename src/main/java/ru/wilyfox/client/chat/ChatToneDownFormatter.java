package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.utils.Formatting;

import java.util.regex.Pattern;

public final class ChatToneDownFormatter {
    private static final Pattern PRIVATE_USE_CHARS = Pattern.compile("[\\uE000-\\uF8FF]");
    private static final Pattern YI_GLYPHS = Pattern.compile("[\\uA000-\\uA4CF]+");
    private static final Pattern RESOURCEPACK_GLYPHS = Pattern.compile("[✦✧◆◇■□▪▫●◉◈⬥⬦⬩⬪⬫]");
    private static final Pattern CHAT_SPLIT = Pattern.compile("^([^:]{1,32})(:\\s+)(.+)$");

    private ChatToneDownFormatter() {
    }

    public static Component format(Component component) {
        if (component == null) {
            return Component.empty();
        }

        String raw = Formatting.stripMinecraftFormatting(component.getString())
                .replace('\u00A0', ' ');

        ChatTab tab = ChatPrefixRouter.resolve(raw);
        String body = ChatPrefixRouter.stripKnownPrefix(raw, tab);
        body = PRIVATE_USE_CHARS.matcher(body).replaceAll("");
        body = YI_GLYPHS.matcher(body).replaceAll("");
        body = RESOURCEPACK_GLYPHS.matcher(body).replaceAll("");
        body = body.replaceAll("\\s+", " ").trim();

        MutableComponent result = Component.empty();
        String prefix = tab.getChatPrefix();

        if (!prefix.isEmpty()) {
            result.append(Component.literal(prefix).withColor(WidgetTheme.TEXT_MUTED));
            if (!body.isEmpty()) {
                result.append(Component.literal(" ").withColor(WidgetTheme.TEXT_MUTED));
            }
        }

        if (body.isEmpty()) {
            return result;
        }

        if (prefix.isEmpty()) {
            result.append(Component.literal(body).withColor(WidgetTheme.TEXT_MUTED));
            return result;
        }

        java.util.regex.Matcher matcher = CHAT_SPLIT.matcher(body);
        if (matcher.matches()) {
            result.append(Component.literal(matcher.group(1)).withColor(WidgetTheme.TEXT_PRIMARY));
            result.append(Component.literal(matcher.group(2)).withColor(WidgetTheme.TEXT_MUTED));
            result.append(Component.literal(matcher.group(3)).withColor(WidgetTheme.TEXT_SECONDARY));
            return result;
        }

        result.append(Component.literal(body).withColor(WidgetTheme.TEXT_SECONDARY));
        return result;
    }
}
