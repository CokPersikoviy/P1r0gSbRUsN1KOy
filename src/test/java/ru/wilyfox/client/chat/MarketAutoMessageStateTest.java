package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MarketAutoMessageStateTest {
    @Test
    void waitsForCooldownPacketAndSafetySecond() {
        MarketAutoMessageState state = new MarketAutoMessageState();

        assertNull(state.nextMessage(List.of("sell"), -1L, 0L, 10_000L));
        assertNull(state.nextMessage(List.of("sell"), 10_000L, 1L, 10_999L));
        assertEquals("$sell", state.nextMessage(List.of("sell"), 10_000L, 1L, 11_000L));
    }

    @Test
    void sendsOnlyOncePerCooldownRevisionAndRotatesMessages() {
        MarketAutoMessageState state = new MarketAutoMessageState();
        List<String> messages = List.of("first", "$second");

        assertEquals("$first", state.nextMessage(messages, 5_000L, 1L, 6_000L));
        state.markQueued(1L);
        assertNull(state.nextMessage(messages, 5_000L, 1L, 20_000L));

        assertEquals("$second", state.nextMessage(messages, 10_000L, 2L, 11_000L));
        state.markQueued(2L);

        assertEquals("$first", state.nextMessage(messages, 15_000L, 3L, 16_000L));
    }

    @Test
    void resetAllowsReadyCooldownToBeUsedAfterReenabling() {
        MarketAutoMessageState state = new MarketAutoMessageState();

        assertEquals("$sell", state.nextMessage(List.of("sell"), 5_000L, 1L, 6_000L));
        state.markQueued(1L);
        state.reset();

        assertEquals("$sell", state.nextMessage(List.of("sell"), 5_000L, 1L, 6_000L));
    }
}
