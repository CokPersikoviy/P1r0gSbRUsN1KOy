package ru.wilyfox.client.moduser;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.chat.BackendChatMessage;
import java.net.http.HttpHeaders;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BackendChatClockTest {
    @Test void immediateConfirmationAndHistoryAcceptBackendClockAheadOfClient() {
        long clientNow = 1_700_000_000_000L, serverNow = clientNow + 78_000;
        var headers = HttpHeaders.of(Map.of("Date", List.of(DateTimeFormatter.RFC_1123_DATE_TIME.format(
                Instant.ofEpochMilli(serverNow).atZone(ZoneOffset.UTC)))), (key, value) -> true);
        var message = new BackendChatMessage(1, "Fox", "Confirmed immediately", serverNow);
        assertThrows(IllegalArgumentException.class, () -> message.validateAt(clientNow));
        long responseNow = SocialWire.responseTime(headers, clientNow);
        assertDoesNotThrow(() -> message.validateAt(responseNow));
        assertDoesNotThrow(message::validate); // Archive checks shape, keeping the server's timestamp.
        assertDoesNotThrow(() -> new BackendChatClient.Page(1, List.of(message), false).validate(responseNow));
        assertEquals(serverNow, message.sentAt());
    }
    @Test void backendClockValidationStillRejectsFutureTimestampsAndMalformedPages() {
        long now = 1_700_000_000_000L;
        var future = new BackendChatMessage(1, "Fox", "Future", now + 60_001);
        assertThrows(IllegalArgumentException.class, () -> new BackendChatClient.Page(1, List.of(future), false).validate(now));
        var first = new BackendChatMessage(1, "Fox", "First", now);
        var second = new BackendChatMessage(2, "Fox", "Second", now);
        assertThrows(IllegalArgumentException.class, () -> new BackendChatClient.Page(1, List.of(second, first), false).validate(now));
        assertThrows(IllegalArgumentException.class, () -> new BackendChatMessage(1, "Bad name", "Message", now).validate());
    }
}
