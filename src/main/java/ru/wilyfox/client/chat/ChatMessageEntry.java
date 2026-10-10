package ru.wilyfox.client.chat;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import java.time.Instant;

/** References the original native message; tabs never manufacture replacement system messages. */
public record ChatMessageEntry(GuiMessage message, Instant timestamp, ChatTab tab, String searchText) {
    public Component component() { return message.content(); }
}
