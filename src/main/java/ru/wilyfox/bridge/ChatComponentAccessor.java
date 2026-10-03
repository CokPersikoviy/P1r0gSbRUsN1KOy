package ru.wilyfox.bridge;

import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.List;

public interface ChatComponentAccessor {
    List<GuiMessage.Line> froghelper$getTrimmedMessages();
}
