package ru.wilyfox.client.protocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldRefreshHandshakeTest {
    @Test void respawnRefreshDoesNotWaitForTwentySecondPayloadTimeout() {
        var state = waiting();
        state.receivedEvoPlusPayload = true;
        state.lastPayloadAt = 100_000;
        state.lastHandshakeAt = 90_000;
        assertTrue(ProtocolTransport.shouldRefreshWorld(state, 100_001));
    }
    @Test void refreshKeepsMinimumHandshakeSpacingAndCancelsWhenNewLocationArrives() {
        var state = waiting(); state.lastHandshakeAt = 100_000;
        assertFalse(ProtocolTransport.shouldRefreshWorld(state, 100_999));
        assertTrue(ProtocolTransport.shouldRefreshWorld(state, 101_000));
        state.currentGameLocation = new DwGameLocation("shaft_125");
        assertFalse(ProtocolTransport.shouldRefreshWorld(state, 101_000));
        assertFalse(state.worldRefreshPending);
    }
    @Test void unchangedFreshSampleCannotHideAnOutstandingWorldRefreshAndResetClearsIt() {
        var state = waiting();
        state.currentGameLocation = new DwGameLocation("bay");
        state.gameLocationRevision++;
        assertTrue(ProtocolTransport.shouldRefreshWorld(state, 100_000));
        state.resetRuntimeState();
        assertFalse(ProtocolTransport.shouldRefreshWorld(state, 100_001));
        assertNull(state.locationBeforeWorldRefresh);
    }
    private static ProtocolState waiting() {
        var state = new ProtocolState(); state.currentGameLocation = new DwGameLocation("bay");
        state.locationBeforeWorldRefresh = state.currentGameLocation;
        state.worldRefreshPending = true; return state;
    }
}
