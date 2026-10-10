package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatPrefixRouterTest {
    @Test void requiresChannelBoundariesInsteadOfMatchingOrdinaryWords() {
        for (String text : new String[]{"Congratulations!", "Trade completed", "Global notice", "Local news", "PMismatch: hi", "ЛСистема: hi"})
            assertEquals(ChatTab.ALL, ChatPrefixRouter.resolve(text), text);
        assertEquals(ChatTab.GLOBAL, ChatPrefixRouter.resolve("[12:34:56] §6[G] Fox: hi"));
        assertEquals(ChatTab.TRADE, ChatPrefixRouter.resolve("T: Fox: hi"));
        assertEquals(ChatTab.LOCAL, ChatPrefixRouter.resolve("ⓁFox: hi"));
        assertEquals(ChatTab.CLAN, ChatPrefixRouter.resolve("[Клан] Fox: hi"));
        assertEquals(ChatTab.PRIVATE, ChatPrefixRouter.resolve("ЛС Fox: hi"));
        assertEquals("CoolPlayer: hi", ChatPrefixRouter.stripKnownPrefix("CoolPlayer: hi", ChatTab.CLAN));
    }
}
