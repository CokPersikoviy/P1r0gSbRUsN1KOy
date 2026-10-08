package ru.wilyfox.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CountdownFormattingTest {
    @Test void secondsHoursAndSpawnBoundaryUseOneClock() {
        long now = 10_000_000L;
        assertEquals("00:00", Formatting.formatMillis(now, now));
        assertEquals("00:00", Formatting.formatMillis(now - 1_000L, now));
        assertEquals("00:59", Formatting.formatMillis(now + 59_999L, now));
        assertEquals("01:00", Formatting.formatMillis(now + 60_000L, now));
        assertEquals("01:00:00", Formatting.formatMillis(now + 3_600_000L, now));
        assertEquals("120:00:00", Formatting.formatMillis(now + 432_000_000L, now));
        assertEquals("00:00", Formatting.formatMillisSigned(now, now));
        assertEquals("-00:00", Formatting.formatMillisSigned(now - 1L, now));
        assertEquals("-01:00:00", Formatting.formatMillisSigned(now - 3_600_000L, now));
    }
}
