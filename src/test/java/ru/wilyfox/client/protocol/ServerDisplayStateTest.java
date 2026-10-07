package ru.wilyfox.client.protocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerDisplayStateTest {
    @Test void freshTabDoesNotWaitForTheNextProtocolPacket() {
        var display = new ServerDisplayState();
        display.protocolUpdated(CurrentServerInfo.fromProtocol("PRISONEVO1", 2));
        display.tabUpdated("PrisonEvo-1 #2");
        display.tabUpdated("PrisonEvo-1 #3");
        assertEquals("PrisonEvo-1 #3", display.displayName());
    }

    @Test void freshProtocolWinsOverThePreviousTabUntilTabChanges() {
        var display = new ServerDisplayState();
        display.tabUpdated("PrisonEvo-1 #2");
        display.protocolUpdated(CurrentServerInfo.fromProtocol("PRISONEVO1", 3));
        display.tabUpdated("Players: 200, PrisonEvo-1 #2");
        assertEquals("PrisonEvo-1 #3", display.displayName());
        display.tabUpdated("PrisonEvo-2 #1");
        assertEquals("PrisonEvo-2 #1", display.displayName());
    }

    @Test void partialTabDoesNotEraseAKnownMirrorForTheSameServer() {
        var display = new ServerDisplayState();
        display.protocolUpdated(CurrentServerInfo.fromProtocol("PRISONEVO1", 2));
        display.tabUpdated("PrisonEvo-1");
        assertEquals("PrisonEvo-1 #2", display.displayName());
        display.tabUpdated("PrisonEvo-2");
        assertEquals("PrisonEvo-2", display.displayName());
    }

    @Test void unknownTabAndUnchangedProtocolDoNotResurrectOldData() {
        var display = new ServerDisplayState();
        var previous = CurrentServerInfo.fromProtocol("PRISONEVO1", 2);
        display.protocolUpdated(previous);
        display.tabUpdated("PrisonEvo-1 #3");
        display.protocolUpdated(previous);
        assertEquals("PrisonEvo-1 #3", display.displayName());
        display.tabUpdated("Welcome to DW");
        assertEquals("PrisonEvo-1 #2", display.displayName());
        display.tabUpdated("Хаб");
        assertEquals("Хаб", display.displayName());
    }
}
