package ru.wilyfox.client.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import ru.wilyfox.bridge.ChatComponentAccessor;
import ru.wilyfox.client.popup.PopUpManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class ChatMessageCopyExtractor {
    private static final Pattern MESSAGE_BODY = Pattern.compile("^([^:]{1,32}:\\s+)(.+)$");

    private ChatMessageCopyExtractor() {
    }

    public static boolean copyHoveredMessage(ChatComponent chat, double mouseX, double mouseY) {
        return copyHoveredMessage(chat, mouseX, mouseY, false);
    }

    public static boolean copyHoveredMessage(ChatComponent chat, double mouseX, double mouseY, boolean fullMessage) {
        if (!(chat instanceof ChatComponentAccessor accessor)) {
            return false;
        }

        List<GuiMessage.Line> visibleMessages = accessor.froghelper$getTrimmedMessages();
        if (visibleMessages.isEmpty()) {
            return false;
        }

        double chatX = accessor.froghelper$screenToChatX(mouseX);
        double chatY = accessor.froghelper$screenToChatY(mouseY);
        int index = accessor.froghelper$getMessageLineIndexAt(chatX, chatY);

        if (index < 0 || index >= visibleMessages.size()) {
            return false;
        }

        List<GuiMessage.Line> messageParts = collectMessageParts(visibleMessages, index);

        String displayedText = ChatMessageSanitizer.forLogic(collectPlainText(messageParts));
        String copied = selectCopiedText(displayedText, fullMessage);
        if (copied.isBlank()) {
            return false;
        }

        Minecraft.getInstance().keyboardHandler.setClipboard(copied);
        PopUpManager.getInstance().notifyChatCopied();
        return true;
    }

    private static List<GuiMessage.Line> collectMessageParts(List<GuiMessage.Line> visibleMessages, int index) {
        int entryStart = index;
        while (entryStart > 0 && !visibleMessages.get(entryStart).endOfEntry()) {
            entryStart--;
        }

        List<GuiMessage.Line> messageParts = new ArrayList<>();
        for (int i = entryStart; i < visibleMessages.size(); i++) {
            GuiMessage.Line line = visibleMessages.get(i);
            if (i > entryStart && line.endOfEntry()) {
                break;
            }
            messageParts.add(line);
        }

        Collections.reverse(messageParts);
        return messageParts;
    }

    private static String collectPlainText(List<GuiMessage.Line> lines) {
        StringBuilder builder = new StringBuilder();
        for (GuiMessage.Line line : lines) {
            line.content().accept((index, style, codePoint) -> {
                builder.appendCodePoint(codePoint);
                return true;
            });
        }
        return builder.toString();
    }

    static String selectCopiedText(String displayedText, boolean fullMessage) {
        if (fullMessage) {
            return displayedText == null ? "" : displayedText;
        }
        return normalizeCopiedText(displayedText);
    }

    private static String normalizeCopiedText(String raw) {
        String text = raw == null ? "" : raw.replace('\u00A0', ' ').strip();
        text = ChatTimestampFormatter.stripTimestampPrefix(text);

        ChatTab tab = ChatPrefixRouter.resolve(text);
        text = ChatPrefixRouter.stripKnownPrefix(text, tab).strip();

        java.util.regex.Matcher matcher = MESSAGE_BODY.matcher(text);
        if (matcher.matches()) {
            return matcher.group(2).strip();
        }

        return text;
    }
}
