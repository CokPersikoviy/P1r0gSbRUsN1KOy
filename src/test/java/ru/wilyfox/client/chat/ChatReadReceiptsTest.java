package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ChatReadReceiptsTest {
    @Test void windowsSharingAChannelHaveIndependentReceipts() {
        var receipts = new ChatReadReceipts<Object>(); var message = new Object();
        receipts.read("fox", message, false);
        assertFalse(receipts.unread("fox", message));
        assertTrue(receipts.unread("global", message));
        receipts.read("all", message, true);
        assertFalse(receipts.unread("global", message));
        assertFalse(receipts.unread("another", message));
    }
    @Test void identityDistinguishesEqualRepeatedMessages() {
        var receipts = new ChatReadReceipts<String>();
        var first = new String("hello"); var second = new String("hello");
        receipts.read("global", first, false);
        assertFalse(receipts.unread("global", first)); assertTrue(receipts.unread("global", second));
        receipts.forget(first); assertTrue(receipts.unread("global", first));
    }
    @Test void deletedWindowsReleaseTheirSlotsWithoutTransferringReadStatus() {
        var receipts = new ChatReadReceipts<Object>(); var message = new Object();
        receipts.read("persistent", message, false);
        for (int i = 0; i < 200; i++) {
            String key = "temporary-" + i;
            assertTrue(receipts.unread(key, message));
            receipts.read(key, message, false);
            receipts.retainWindows(Set.of("persistent"));
        }
        assertFalse(receipts.unread("persistent", message));
        receipts.clear(); assertTrue(receipts.unread("persistent", message));
    }
}
