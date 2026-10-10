package ru.wilyfox.client.moduser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SocialWireTest {
    @Test void timerMetadataIsOptionalAndProtocolBadgeUsesStrictFiftyThreshold() {
        String id = "a".repeat(64);
        assertNull(SocialWire.JSON.fromJson("{\"id\":\"" + id + "\",\"name\":\"Fox\"}", SocialWire.Player.class).timerCount());
        assertFalse(new SocialWire.Player(id, "Fox", "HUB0:0", 50, true).protocolBadge());
        assertTrue(new SocialWire.Player(id, "Fox", "PRISONEVO1:2", 51, true).protocolBadge());
        assertFalse(new SocialWire.Player(id, "Fox", "HUB0:0", 51, false).protocolBadge());
        for (Integer count : List.of(-1, 513)) {
            var snapshot = new SocialWire.Snapshot(1, "presence.snapshot", 1, "HUB0:0",
                    List.of(new SocialWire.Player(id, "Fox", "HUB0:0", count, true)), true);
            assertThrows(IllegalArgumentException.class, () -> snapshot.validatedNames("HUB0:0"));
        }
        PresenceStore.replacePlayers(List.of(new SocialWire.Player(id, "Fox", "HUB0:0", 51, true)));
        assertTrue(PresenceStore.player("fOx").protocolBadge());
        PresenceStore.replace(List.of("Fox"));
        assertNull(PresenceStore.player("Fox"));
    }
    @Test void timerResponsesKeepAbsoluteDeadlinesAndRejectReplayWrongIdentityAndOversizedPayloads() {
        long now = 1_000_000;
        String id = "a".repeat(64);
        var timer = new SocialWire.Timer("Boss", 125, now + 60_000);
        var valid = new SocialWire.TimerResponse(1, id, "Fox", "PRISONEVO1:2", now, List.of(timer));
        assertEquals(List.of(timer), valid.validatedTimers(id, "PRISONEVO1:2", now));
        assertThrows(IllegalArgumentException.class, () -> valid.validatedTimers("b".repeat(64), "PRISONEVO1:2", now));
        assertThrows(IllegalArgumentException.class, () -> valid.validatedTimers(id, "HUB0:0", now));
        assertThrows(IllegalArgumentException.class, () -> valid.validatedTimers(id, "PRISONEVO1:2", now + 46_000));
        assertThrows(IllegalArgumentException.class, () -> new SocialWire.TimerResponse(1, id, "Fox", "PRISONEVO1:2", now,
                List.of(timer, timer)).validatedTimers(id, "PRISONEVO1:2", now));
        assertThrows(IllegalArgumentException.class, () -> new SocialWire.Timer("Boss\n", 125, now + 1).validate(now));
        assertThrows(IllegalArgumentException.class, () -> new SocialWire.Timer("Boss", 125, now + 604_800_001).validate(now));
        assertTrue(new SocialWire.TimerResponse(1, id, "Fox", "HUB0:0", now,
                List.of(new SocialWire.Timer("Expired", 125, now - 1))).validatedTimers(id, "HUB0:0", now).isEmpty());
    }
    @AfterEach void clear() { PresenceStore.clear(); }

    @Test void rosterIsOnlyCurrentSnapshotAndNeverHistoricalDiscovery() {
        PresenceStore.replace(List.of("Fox", "Frog"));
        assertTrue(PresenceStore.isKnown("fOx"));
        PresenceStore.replace(List.of("Frog"));
        assertFalse(PresenceStore.isKnown("Fox"));
        assertEquals(List.of("Frog"), PresenceStore.knownDisplayNames());
        assertThrows(UnsupportedOperationException.class, () -> PresenceStore.knownDisplayNames().add("Fake"));
        PresenceStore.clear();
        assertEquals(0, PresenceStore.knownCount());
    }

    @Test void rejectsWrongScopeVersionAndInvalidNamesBeforeReplacingRoster() {
        String id = "a".repeat(64);
        SocialWire.Snapshot valid = new SocialWire.Snapshot(1, "presence.snapshot", 1, "HUB0:0",
                List.of(new SocialWire.Player(id, "Fox")));
        assertEquals(List.of("Fox"), valid.validatedNames("HUB0:0"));
        assertThrows(IllegalArgumentException.class, () -> valid.validatedNames("PRISONEVO1:1"));
        for (String name : List.of("/op Fox", "", "ⒻFox", "x".repeat(17))) {
            var snapshot = new SocialWire.Snapshot(1, "presence.snapshot", 1, "HUB0:0",
                    List.of(new SocialWire.Player(id, name)));
            assertThrows(IllegalArgumentException.class, () -> snapshot.validatedNames("HUB0:0"));
        }
        assertThrows(IllegalArgumentException.class, () -> new SocialWire.Snapshot(2, "presence.snapshot", 1,
                "HUB0:0", List.of()).validatedNames("HUB0:0"));
        PresenceStore.replace(List.of("Fox"));
        assertThrows(IllegalArgumentException.class, () -> PresenceStore.replace(List.of("Frog", "/op Fox")));
        assertTrue(PresenceStore.isKnown("Fox"));
        assertFalse(PresenceStore.isKnown("Frog"));
    }

    @Test void rejectsExpiredOrWrongPlayerSessionAndUnboundedBearer() {
        new SocialWire.Session("a".repeat(43), 1_500, "Fox").validate("fOx", 1_000);
        assertThrows(IllegalArgumentException.class, () -> new SocialWire.Session("a".repeat(43), 1_500, "Admin").validate("Fox", 1_000));
        assertThrows(IllegalArgumentException.class, () -> new SocialWire.Session("a".repeat(43), 900, "Fox").validate("Fox", 1_000));
        assertThrows(IllegalArgumentException.class, () -> new SocialWire.Session("a".repeat(43), 1_800, "Fox").validate("Fox", 1_000));
        assertThrows(IllegalArgumentException.class, () -> new SocialWire.Session("fake\r\nHeader", 1_500, "Fox").validate("Fox", 1_000));
    }
}
