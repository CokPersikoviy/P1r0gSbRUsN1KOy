package ru.wilyfox.client.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class ResourceTimerStoreTest {
    @TempDir Path directory;
    @Test void parsesActualHologramAndRejectsUnrelatedNames() {
        assertEquals(new ResourceCountdown(80_820_000L, 60_000L), ResourceCountdown.parse("§fДо следующего прокрута: §e22 ч. 27 мин."));
        assertEquals(new ResourceCountdown(5000, 1000), ResourceCountdown.parse("До следующего прокрута: 5 сек."));
        assertEquals(new ResourceCountdown(0, 60_000), ResourceCountdown.parse("До следующего прокрута: 0 мин."));
        assertNull(ResourceCountdown.parse("До следующего ресурса: 23 ч. 23 мин."));
        assertNull(ResourceCountdown.parse("Новые кредиты через: 1д"));
        assertNull(ResourceCountdown.parse("До следующего прокрута: нет"));
        assertNull(ResourceCountdown.parse("До следующего прокрута: 999999999999999999999 ч."));
    }
    @Test void absoluteDeadlineSurvivesRestartAndRemindersDoNotSpamReconnect() throws Exception {
        var store = new ResourceTimerStore();
        long now = 1_700_000_000_000L;
        var countdown = new ResourceCountdown(60_000, 60_000);
        assertTrue(store.observe("WilyFox", countdown, now));
        assertFalse(store.observe("wilyfox", countdown, now + 1000));
        assertTrue(store.reminders("WilyFox", now + 59_999, 5).isEmpty());
        Path file = directory.resolve("timer.json");
        ResourceTimerStore.save(file, store.snapshot());
        var restarted = new ResourceTimerStore(); restarted.load(file);
        assertEquals(now + 60_000, restarted.entry("WILYFOX").endsAt());
        assertEquals(1, restarted.reminders("WilyFox", now + 60_000, 5).size());
        ResourceTimerStore.save(file, restarted.snapshot());
        var reconnected = new ResourceTimerStore(); reconnected.load(file);
        assertTrue(reconnected.reminders("WilyFox", now + 60_001, 5).isEmpty());
        assertFalse(reconnected.observe("WilyFox", new ResourceCountdown(0, 60_000), now + 200_000));
        assertEquals(now + 60_000, reconnected.entry("WilyFox").lastNoticeAt());
        assertTrue(reconnected.reminders("WilyFox", now + 359_999, 5).isEmpty());
        assertEquals(1, reconnected.reminders("WilyFox", now + 360_000, 5).size());
        assertTrue(reconnected.reminders("OtherPlayer", now + 400_000, 5).isEmpty());
    }
    @Test void parsesDailyCreditsItemAndCompactDayFormat() {
        assertEquals(new ResourceCountdown(86_400_000, 86_400_000), ResourceCountdown.parseCredits("§eНовые кредиты через: §f1д"));
        assertEquals(new ResourceCountdown(83_460_000, 60_000), ResourceCountdown.parseCredits("Новые кредиты через: 23 ч. 11 мин."));
        assertEquals(new ResourceCountdown(90_061_000, 1000), ResourceCountdown.parseCredits("Новые кредиты через: 1д 1ч 1мин 1с"));
        assertNull(ResourceCountdown.parseCredits("Провести 1 час на режимах"));
        assertNull(ResourceCountdown.parseCredits("До следующего ресурса: 1д"));
        assertNull(ResourceCountdown.parseCredits("Новые кредиты через: 1д какой-то текст"));
    }
    @Test void oneDayStartsNewCycleAfterReadyEvenWithCoarsePrecision() {
        var store = new ResourceTimerStore(); long now = 1_700_000_000_000L;
        store.observe("one", new ResourceCountdown(1000, 1000), now);
        assertEquals(1, store.reminders("one", now + 2000, 10).size());
        assertTrue(store.observe("one", ResourceCountdown.parseCredits("Новые кредиты через: 1д"), now + 3000));
        assertEquals(now + 3000 + 86_400_000, store.entry("one").endsAt());
        assertEquals(0, store.entry("one").lastNoticeAt());
        assertTrue(store.reminders("one", now + 3000, 10).isEmpty());
        assertTrue(store.observe("one", ResourceCountdown.parseCredits("Новые кредиты через: 23 ч. 11 мин."), now + 3000));
        assertEquals(now + 3000 + 83_460_000, store.entry("one").endsAt());
    }
    @Test void newRollResetsReminderAndMissedOfflineExpiryNotifiesOnce() {
        var store = new ResourceTimerStore(); long now = 1_700_000_000_000L;
        store.observe("one", new ResourceCountdown(1000, 1000), now);
        assertEquals(1, store.reminders("one", now + 86_400_000, 10).size());
        assertTrue(store.reminders("one", now + 86_400_001, 10).isEmpty());
        assertTrue(store.observe("one", new ResourceCountdown(86_400_000, 60_000), now + 86_400_002));
        assertEquals(0, store.entry("one").lastNoticeAt());
        assertTrue(store.reminders("one", now + 86_400_003, 10).isEmpty());
    }
}
