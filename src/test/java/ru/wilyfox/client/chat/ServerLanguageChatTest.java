package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerLanguageChatTest {
    @Test
    void boosterThanksRecognizesBothServerLanguagesAndIgnoresPlayerChat() {
        assertTrue(AutoThanks.isGlobalBoosterActivation("[VIP] Fox активировал глобальный бустер опыта"));
        assertTrue(AutoThanks.isGlobalBoosterActivation("§a[VIP] Fox activated the global experience booster"));
        assertTrue(AutoThanks.isGlobalBoosterActivation("Fox активировал глобальный бустер опыта: x2"));
        assertTrue(AutoThanks.isGlobalBoosterActivation("Fox activated the global experience booster: x2"));
        assertFalse(AutoThanks.isGlobalBoosterActivation("Fox: activated the global experience booster"));
        assertFalse(AutoThanks.isGlobalBoosterActivation("No active boosters"));
        assertFalse(AutoThanks.isGlobalBoosterActivation(null));
    }

    @Test
    void localTimestampDoesNotTurnABoosterBroadcastIntoPlayerChat() {
        assertTrue(AutoThanks.isGlobalBoosterActivation("§7[23:09:54] §aFox активировал глобальный бустер опыта"));
        assertTrue(AutoThanks.isGlobalBoosterActivation("[08:00:01] [VIP] Fox activated the global money booster"));
        assertFalse(AutoThanks.isGlobalBoosterActivation("[08:00:01] Fox: activated the global money booster"));
    }

    @Test
    void higherBitingRecognizesBothLanguagesAndPreservesLocation() {
        assertEquals("End Ocean", HigherBitingNotifier.higherBitingLocation("Nibble rate has increased in location: End Ocean!"));
        assertEquals("Океан края", HigherBitingNotifier.higherBitingLocation("§aНа локации \"Океан края\" повышенный клёв!"));
        assertNull(HigherBitingNotifier.higherBitingLocation("Fox: Nibble rate has increased in location: End Ocean!"));
        assertNull(HigherBitingNotifier.higherBitingLocation(null));
    }
}
