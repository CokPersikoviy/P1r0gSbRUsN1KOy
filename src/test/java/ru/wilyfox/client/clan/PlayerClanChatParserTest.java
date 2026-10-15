package ru.wilyfox.client.clan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PlayerClanChatParserTest {
    @ParameterizedTest
    @ValueSource(strings = {"1", "520", "521", "541", "590", "999", "1000", "2147483648", "001"})
    void clanObservationDoesNotDependOnTheServerLevelCap(String level) {
        var entry = PlayerClanChatParser.parse("[01:09:21] Ⓜ ꀵ TestPlayer ဣ  [Новый Клан] [" + level + "]: hello");
        assertNotNull(entry);
        assertEquals("TestPlayer", entry.playerName());
        assertEquals("Новый Клан", entry.clanName());
    }

    @Test
    void highLevelClanlessMessageRemainsAnExplicitObservation() {
        var entry = PlayerClanChatParser.parse("Ⓖ ꀕ TestPlayer [590]: hello [Old Clan] [100]");
        assertNotNull(entry);
        assertEquals("TestPlayer", entry.playerName());
        assertNull(entry.clanName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "000", "-1", "1.5", "level", ""})
    void invalidLevelDoesNotTurnUnrelatedTextIntoAClanObservation(String level) {
        assertNull(PlayerClanChatParser.parse("TestPlayer [Other Clan] [" + level + "]: hello"));
    }

    @Test
    void bracketsInMessageTextCannotSupplyAMissingSenderLevel() {
        assertNull(PlayerClanChatParser.parse("TestPlayer: [Other Clan] [590]"));
    }
}
