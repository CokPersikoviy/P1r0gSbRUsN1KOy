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
        assertFalse(AutoThanks.isGlobalBoosterActivation("Fox: activated the global experience booster"));
        assertFalse(AutoThanks.isGlobalBoosterActivation("No active boosters"));
        assertFalse(AutoThanks.isGlobalBoosterActivation(null));
    }

    @Test
    void higherBitingRecognizesBothLanguagesAndPreservesLocation() {
        assertEquals("End Ocean", HigherBitingNotifier.higherBitingLocation("Nibble rate has increased in location: End Ocean!"));
        assertEquals("Океан края", HigherBitingNotifier.higherBitingLocation("§aНа локации \"Океан края\" повышенный клёв!"));
        assertNull(HigherBitingNotifier.higherBitingLocation("Fox: Nibble rate has increased in location: End Ocean!"));
        assertNull(HigherBitingNotifier.higherBitingLocation(null));
    }
}
