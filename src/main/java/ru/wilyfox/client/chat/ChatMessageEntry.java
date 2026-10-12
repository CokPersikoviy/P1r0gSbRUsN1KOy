package ru.wilyfox.client.chat;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import java.time.Instant;

/** References the original native message; tabs never manufacture replacement system messages. */
public record ChatMessageEntry(GuiMessage message, Instant timestamp, ChatTab tab, String searchText, long receivedNanos,
                               String filterText) {
    public ChatMessageEntry(GuiMessage message, Instant timestamp, ChatTab tab, String searchText, long receivedNanos) {
        this(message, timestamp, tab, searchText, receivedNanos, plainText(message.content()));
    }
    public static String plainText(Component content) {
        return ChatTimestampFormatter.stripTimestampPrefix(ru.wilyfox.utils.Formatting.stripMinecraftFormatting(
                ChatMessageSanitizer.forLogic(content.getString())).replace('\u00a0', ' ')).strip();
    }
    public Component component() { return message.content(); }
}
