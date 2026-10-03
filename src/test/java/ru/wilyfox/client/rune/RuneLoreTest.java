package ru.wilyfox.client.rune;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuneLoreTest {
    @Test
    void recognizesBothReferenceLanguagesForSetSelection() {
        assertTrue(RuneLore.isActiveSet("Используется"));
        assertTrue(RuneLore.isActiveSet("Used"));
        assertTrue(RuneLore.canUseSet("Нажмите, чтобы использовать"));
        assertTrue(RuneLore.canUseSet("Click to use"));
        assertFalse(RuneLore.isActiveSet("Click to use"));
        assertFalse(RuneLore.canUseSet("Used"));
        assertFalse(RuneLore.isActiveSet(null));
        assertFalse(RuneLore.canUseSet(null));
    }
}
