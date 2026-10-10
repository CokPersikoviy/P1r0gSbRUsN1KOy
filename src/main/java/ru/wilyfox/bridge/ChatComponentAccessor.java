package ru.wilyfox.bridge;

import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.List;

public interface ChatComponentAccessor {
    List<GuiMessage.Line> froghelper$getTrimmedMessages();
    List<GuiMessage> froghelper$getAllMessages();
    int froghelper$getScroll();
    boolean froghelper$getNewMessageSinceScroll();
    void froghelper$setNewMessageSinceScroll(boolean value);
    void froghelper$addDisplayMessage(GuiMessage message);
}
