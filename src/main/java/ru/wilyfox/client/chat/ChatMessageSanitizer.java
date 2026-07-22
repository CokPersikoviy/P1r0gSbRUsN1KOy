package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import ru.wilyfox.client.moduser.ModUserBadge;
import ru.wilyfox.client.moduser.ModUserMarker;

public final class ChatMessageSanitizer {
    private ChatMessageSanitizer() {
    }

    public static String forLogic(String text) {
        return ModUserBadge.strip(ModUserMarker.strip(text));
    }

    public static Component forLogic(Component component) {
        return ModUserBadge.strip(ModUserMarker.strip(component));
    }
}
