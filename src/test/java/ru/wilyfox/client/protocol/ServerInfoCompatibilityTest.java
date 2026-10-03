package ru.wilyfox.client.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerInfoCompatibilityTest {
    @Test
    void repeatedServerInfoKeepsContextRevisionButChangingMirrorUpdatesState() {
        ProtocolState state = new ProtocolState();
        CurrentServerInfo first = CurrentServerInfo.fromProtocol("PRISONEVO1", 1);
        ProtocolPayloadHandlers.applyServerInfo(state, first);
        long firstRevision = state.worldContextRevision;
        ProtocolPayloadHandlers.applyServerInfo(state, CurrentServerInfo.fromProtocol("PRISONEVO1", 1));
        assertEquals(firstRevision, state.worldContextRevision);

        CurrentServerInfo second = CurrentServerInfo.fromProtocol("PRISONEVO1", 2);
        ProtocolPayloadHandlers.applyServerInfo(state, second);
        assertEquals(firstRevision + 1, state.worldContextRevision);
        assertEquals(second, state.currentServerInfo);
    }
}
