package ru.wilyfox.client.chat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoBossAnnouncerTest {
    @ParameterizedTest
    @CsvSource({
            "15000, 15с", "14999, 15с", "14001, 15с", "14000, 14с",
            "20000, 20с", "19999, 20с", "19001, 20с", "19000, 19с",
            "1, 1с", "0, 0с", "-1, 0с",
            "59999, 1м 00с", "60000, 1м 00с", "60001, 1м 01с",
            "3599999, 1ч 00м 00с", "3600000, 1ч 00м 00с"
    })
    void respawnAnnouncementRoundsRemainingTimeUp(long millis, String expected) throws ReflectiveOperationException {
        var formatter = AutoBossAnnouncer.class.getDeclaredMethod("formatDuration", long.class);
        formatter.setAccessible(true);
        assertEquals(expected, formatter.invoke(null, millis));
    }
}
