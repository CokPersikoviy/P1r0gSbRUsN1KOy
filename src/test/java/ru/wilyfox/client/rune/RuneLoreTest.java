package ru.wilyfox.client.rune;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuneLoreTest {
    @Test void skipsSlotControlsButKeepsRealRunesIncludingMiningRunes() {
        for (String name : java.util.List.of("Разблокировать слот для шахтёрской руны", "§eРазблокировать слот для шахтерской руны",
                " Пустой\u00a0слот для руны ", "Заблокированный слот", "Нажмите, чтобы разблокировать слот",
                "Unlock mining rune slot", "Empty slot", "Locked slot", "Click to unlock", "Поместите руну в слот", "")) {
            assertTrue(RuneLore.isSlotPlaceholder(name), name);
        }
        assertTrue(RuneLore.isSlotPlaceholder(null));
        for (String name : java.util.List.of("Ветер II", "Железо II", "Валькирия II", "Добытчик II", "§6Шахтёрская руна III", "Mining rune", "Wind II")) {
            assertFalse(RuneLore.isSlotPlaceholder(name), name);
        }
    }
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
