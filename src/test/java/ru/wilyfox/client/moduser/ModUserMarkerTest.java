package ru.wilyfox.client.moduser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModUserMarkerTest {
    @Test
    void appendsBeaconWhenSocialsAreEnabled() {
        assertEquals("hello" + ModUserStorage.MARKER, ModUserMarker.appendToOutgoing("hello", true));
    }

    @Test
    void doesNotAppendBeaconWhenSocialsAreDisabled() {
        assertEquals("hello", ModUserMarker.appendToOutgoing("hello", false));
    }

    @Test
    void neverAppendsBeaconToCommandsOrProtocolMessages() {
        assertEquals("/spawn", ModUserMarker.appendToOutgoing("/spawn", true));
        assertEquals("{fhmu:test}", ModUserMarker.appendToOutgoing("{fhmu:test}", true));
    }
}
