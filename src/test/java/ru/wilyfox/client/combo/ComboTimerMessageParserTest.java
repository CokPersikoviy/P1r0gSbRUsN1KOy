package ru.wilyfox.client.combo;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboTimerMessageParserTest {
    @Test
    void parsesEvoPlusServerWarning() {
        OptionalLong parsed = ComboTimerMessageParser.parseRemainingSeconds(
                "Комбо закончится через 27 секунд. Продолжите копать, чтобы не потерять его."
        );

        assertTrue(parsed.isPresent());
        assertEquals(27L, parsed.getAsLong());
    }

    @Test
    void ignoresUnrelatedMessages() {
        assertTrue(ComboTimerMessageParser.parseRemainingSeconds("Комбо увеличено").isEmpty());
    }
}
