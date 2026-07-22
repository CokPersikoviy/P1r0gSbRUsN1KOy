package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.moduser.ModUserBadge;
import ru.wilyfox.client.moduser.ModUserStorage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatMessageCopyExtractorTest {
    @Test
    void fullMessageCopyPreservesDisplayedTextExceptFrogMarkers() {
        String message = "[12:34:56] [Clan] [VIP] Fox: hello\u00A0world";
        String displayed = ModUserBadge.prefix(Component.literal(message + ModUserStorage.MARKER)).getString();

        assertEquals(message, ChatMessageCopyExtractor.selectCopiedText(
                ChatMessageSanitizer.forLogic(displayed),
                true
        ));
    }

    @Test
    void normalCopyStillExtractsOnlyMessageBody() {
        String displayed = "[12:34:56] Fox: hello world";

        assertEquals("hello world", ChatMessageCopyExtractor.selectCopiedText(displayed, false));
    }
}
