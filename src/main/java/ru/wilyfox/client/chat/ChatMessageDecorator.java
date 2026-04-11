package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import ru.wilyfox.client.hud.config.ConfigManager;

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

        Component result = component;
        if (ConfigManager.get().render.toneDownChat) {
            result = ChatToneDownFormatter.format(result);
        }

        if (ConfigManager.get().render.chatTimestamps) {
            result = prependTimestamp(result);
        }

        return result;
    }

    private static Component prependTimestamp(Component component) {
        MutableComponent prefixed = Component.empty();
        prefixed.append(ChatTimestampFormatter.createTimestampPrefix(TIMESTAMP_OVERRIDE.get()));
        prefixed.append(component.copy());
        return prefixed;
    }
}
