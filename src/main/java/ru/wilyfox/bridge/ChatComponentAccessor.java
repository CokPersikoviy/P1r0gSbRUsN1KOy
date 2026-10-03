package ru.wilyfox.bridge;

import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.List;

public interface ChatComponentAccessor {
    List<GuiMessage.Line> froghelper$getTrimmedMessages();
    double froghelper$screenToChatX(double mouseX);
    double froghelper$screenToChatY(double mouseY);
    int froghelper$getMessageLineIndexAt(double chatX, double chatY);
}
