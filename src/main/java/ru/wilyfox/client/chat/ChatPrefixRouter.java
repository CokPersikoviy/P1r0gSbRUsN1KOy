package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import ru.wilyfox.utils.Formatting;

import java.util.regex.Pattern;

public final class ChatPrefixRouter {
    private static final Pattern BRACKETED_PREFIX = Pattern.compile("^\\[[^\\]]+\\]\\s*");

    private ChatPrefixRouter() {
    }

    public static ChatTab resolve(Component component) {
        return resolve(component.getString());
    }

    public static ChatTab resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return ChatTab.ALL;
        }

        String text = Formatting.stripMinecraftFormatting(ChatMessageSanitizer.forLogic(raw)).replace('\u00A0', ' ');
        text = ChatTimestampFormatter.stripTimestampPrefix(text).stripLeading();

        for (ChatTab tab : ChatTab.values()) {
            if (tab == ChatTab.ALL) {
                continue;
            }

            for (String prefix : tab.getResolvePrefixes()) {
                if (matches(text, prefix)) {
                    return tab;
                }
            }
        }

        return ChatTab.ALL;
    }

    private static boolean matches(String text, String prefix) {
        return text.startsWith(prefix)
                || text.startsWith("[" + prefix + "]")
                || text.startsWith(prefix + " ")
                || text.startsWith(prefix + ":");
    }

    public static String stripKnownPrefix(String text, ChatTab tab) {
        String clean = ChatTimestampFormatter.stripTimestampPrefix(ChatMessageSanitizer.forLogic(text)).stripLeading();

        for (String candidate : tab.getResolvePrefixes()) {
            if (candidate.isEmpty()) {
                continue;
            }

            if (clean.startsWith("[" + candidate + "]")) {
                return clean.substring(candidate.length() + 2).stripLeading();
            }

            if (clean.startsWith(candidate + " ")) {
                return clean.substring(candidate.length() + 1).stripLeading();
            }

            if (clean.startsWith(candidate + ":")) {
                return clean.substring(candidate.length() + 1).stripLeading();
            }

            if (clean.startsWith(candidate)) {
                return clean.substring(candidate.length()).stripLeading();
            }
        }

        return BRACKETED_PREFIX.matcher(clean).replaceFirst("").stripLeading();
    }
}
