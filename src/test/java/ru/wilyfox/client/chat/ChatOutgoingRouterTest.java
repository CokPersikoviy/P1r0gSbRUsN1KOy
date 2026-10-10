package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatOutgoingRouterTest {
    @Test void choosesActualServerChannelWithoutChangingExplicitCommandsOrPrefixes() {
        assertEquals("!hello", ChatOutgoingRouter.format("hello", ChatTab.GLOBAL, true, ""));
        assertEquals("$hello", ChatOutgoingRouter.format("hello", ChatTab.TRADE, true, ""));
        assertEquals("@hello", ChatOutgoingRouter.format("hello", ChatTab.CLAN, true, ""));
        assertEquals("hello", ChatOutgoingRouter.format("hello", ChatTab.LOCAL, true, ""));
        assertEquals("hello", ChatOutgoingRouter.format("hello", ChatTab.ALL, true, ""));
        for (ChatTab tab : ChatTab.values()) if (tab != ChatTab.FH) for (String input : new String[]{"/msg Fox hi", "!hi", "$sell", "@clan", "/fhprof 15"})
            assertEquals(input, ChatOutgoingRouter.format(input, tab, true, "/r "));
        assertEquals("hello", ChatOutgoingRouter.format("hello", ChatTab.CLAN, false, ""));
    }
    @Test void backendChatNeverProducesGameChatOrCommandText() {
        for (boolean onDw : new boolean[]{true, false}) for (String text : new String[]{"hello", "/fhprof 15", "/msg Fox secret", "!global", "@clan", "$trade"})
            assertNull(ChatOutgoingRouter.format(text, ChatTab.FH, onDw, "/r "));
    }
    @Test void neverFallsBackFromPrivateToPublicChatOrSilentlyTruncatesText() {
        assertNull(ChatOutgoingRouter.format("private text", ChatTab.PRIVATE, true, ""));
        assertEquals("/reply private text", ChatOutgoingRouter.format("private text", ChatTab.PRIVATE, true, "/reply "));
        assertNull(ChatOutgoingRouter.format("x".repeat(256), ChatTab.GLOBAL, true, ""));
        assertEquals("!" + "x".repeat(255), ChatOutgoingRouter.format("x".repeat(255), ChatTab.GLOBAL, true, ""));
        assertEquals("", ChatOutgoingRouter.format("  ", ChatTab.PRIVATE, true, ""));
    }
}
