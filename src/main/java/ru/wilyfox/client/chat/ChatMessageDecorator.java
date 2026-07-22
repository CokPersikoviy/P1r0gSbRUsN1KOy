package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import ru.wilyfox.client.clan.PlayerClanChatParser;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.moduser.ModUserBadge;
import ru.wilyfox.client.moduser.ModUserMarker;
import ru.wilyfox.client.moduser.ModUserStorage;

import java.time.Instant;

public final class ChatMessageDecorator {
    private static final ThreadLocal<Instant> TIMESTAMP_OVERRIDE = new ThreadLocal<>();

    private ChatMessageDecorator() {
    }

    public static void withTimestamp(Instant timestamp, Runnable action) {
        if (action == null) {
            return;
        }

        TIMESTAMP_OVERRIDE.set(timestamp);
        try {
            action.run();
        } finally {
            TIMESTAMP_OVERRIDE.remove();
        }
    }

    public static Component decorate(Component component) {
        if (component == null) {
            return Component.empty();
        }

        // Detect the mod beacon on the RAW line (before we strip it below); this also seeds the mesh.
        if (!ChatTabManager.getInstance().isRebuilding()) {
            ModUserStorage.captureFromChat(component);
        }

        // Decide the badge from the RAW line's sender (before we prepend a timestamp etc.).
        boolean modUserBadge = ConfigManager.get().render.modUserBadge && isKnownSender(component);

        // Strip the visible Ⓕ beacon out of what actually gets displayed.
        Component result = ModUserMarker.strip(component);
        if (ConfigManager.get().render.toneDownChat) {
            result = ChatToneDownFormatter.format(result);
        }

        if (modUserBadge) {
            result = insertModUserBadge(result);
        }

        if (ConfigManager.get().render.chatTimestamps) {
            result = prependTimestamp(result);
        }

        return result;
    }

    static Component insertModUserBadge(Component component) {
        String text = ChatMessageSanitizer.forLogic(component.getString());
        ChatTab tab = ChatPrefixRouter.resolve(text);
        int insertionIndex = findPrefixEnd(text, tab);
        return ModUserBadge.insert(component, insertionIndex);
    }

    private static int findPrefixEnd(String text, ChatTab tab) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        if (tab != ChatTab.ALL) {
            for (String candidate : tab.getResolvePrefixes()) {
                int end = matchedPrefixEnd(text, candidate);
                if (end >= 0) {
                    return skipWhitespace(text, end);
                }
            }
        }

        int firstWhitespace = firstWhitespace(text);
        if (firstWhitespace >= 0) {
            return skipWhitespace(text, firstWhitespace);
        }

        int colon = text.indexOf(':');
        return colon >= 0 ? colon + 1 : text.length();
    }

    private static int matchedPrefixEnd(String text, String candidate) {
        String bracketed = "[" + candidate + "]";
        if (text.startsWith(bracketed)) {
            return bracketed.length();
        }
        if (!text.startsWith(candidate)) {
            return -1;
        }

        int end = candidate.length();
        if (end == text.length()) {
            return end;
        }

        char next = text.charAt(end);
        if (next == ':') {
            return end + 1;
        }
        return Character.isWhitespace(next) ? end : -1;
    }

    private static int firstWhitespace(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String text, int index) {
        int current = index;
        while (current < text.length() && Character.isWhitespace(text.charAt(current))) {
            current++;
        }
        return current;
    }

    private static boolean isKnownSender(Component component) {
        String sender = PlayerClanChatParser.senderNameLenient(component);
        return sender != null && ModUserStorage.isKnown(sender);
    }

    private static Component prependTimestamp(Component component) {
        MutableComponent prefixed = Component.empty();
        prefixed.append(ChatTimestampFormatter.createTimestampPrefix(TIMESTAMP_OVERRIDE.get()));
        prefixed.append(component.copy());
        return prefixed;
    }
}
