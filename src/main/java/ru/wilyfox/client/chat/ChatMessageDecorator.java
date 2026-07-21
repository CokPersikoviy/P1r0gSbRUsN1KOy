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
        ModUserStorage.captureFromChat(component);

        // Decide the badge from the RAW line's sender (before we prepend a timestamp etc.).
        boolean modUserBadge = ConfigManager.get().render.modUserBadge && isKnownSender(component);

        // Strip the visible Ⓕ beacon out of what actually gets displayed.
        Component result = ModUserMarker.strip(component);
        if (ConfigManager.get().render.toneDownChat) {
            result = ChatToneDownFormatter.format(result);
        }

        if (ConfigManager.get().render.chatTimestamps) {
            result = prependTimestamp(result);
        }

        if (modUserBadge) {
            result = ModUserBadge.prefix(result);
        }

        return result;
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
