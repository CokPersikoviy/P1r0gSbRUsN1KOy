package ru.wilyfox.client.clan;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerClanStorageTest {
    @Test
    void changedClanReplacesExistingLocalEntry() {
        Map<String, PlayerClanEntry> entries = new LinkedHashMap<>();
        assertTrue(PlayerClanStorage.updateEntry(entries, "Player", "Old Clan", 1_000L));

        assertTrue(PlayerClanStorage.updateEntry(entries, "Player", "New Clan", 2_000L));

        PlayerClanEntry entry = entries.get("player");
        assertEquals("New Clan", entry.clanName);
        assertEquals(2_000L, entry.updatedAt);
    }

    @Test
    void clanlessObservationClearsExistingLocalClan() {
        Map<String, PlayerClanEntry> entries = new LinkedHashMap<>();
        PlayerClanStorage.updateEntry(entries, "Player", "Old Clan", 1_000L);

        assertTrue(PlayerClanStorage.updateEntry(entries, "Player", null, 2_000L));

        assertNull(entries.get("player").clanName);
    }
}
