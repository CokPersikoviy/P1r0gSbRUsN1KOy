package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatProtocolRouteTest {
    @Test
    void acceptsIncomingMessageAddressedToSelfAlias() {
        assertEquals("Sender_1", ChatProtocolRoute.extractIncomingSender(
                "ЛС | Sender_1 » Я: {fhmu:test}", "{fhmu:"
        ));
    }

    @Test
    void rejectsOwnOutgoingEchoAndOtherRecipient() {
        assertNull(ChatProtocolRoute.extractIncomingSender(
                "ЛС | Я » Target: {fhmu:test}", "{fhmu:"
        ));
        assertNull(ChatProtocolRoute.extractIncomingSender(
                "ЛС | Sender » Target: {fhmu:test}", "{fhmu:"
        ));
    }

    @Test
    void retainsLegacyRouteWithoutArrow() {
        assertEquals("Sender", ChatProtocolRoute.extractIncomingSender(
                "ЛС | Sender: {fhmu:test}", "{fhmu:"
        ));
    }
}
