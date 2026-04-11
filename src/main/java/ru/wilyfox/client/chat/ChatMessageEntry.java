package ru.wilyfox.client.chat;


import net.minecraft.network.chat.Component;

import java.time.Instant;

public record ChatMessageEntry(Component component, Instant timestamp) {
}
