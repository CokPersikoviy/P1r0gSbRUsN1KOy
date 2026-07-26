package ru.wilyfox.client.popup;

import org.junit.jupiter.api.Test;
import ru.wilyfox.boss.BossInfo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossSpawnTrackerTest {
    private static final long NOW = 1_000_000L;

    @Test
    void timestampJitterAndLevelRefreshDoNotDuplicateSameSpawn() {
        BossSpawnTracker tracker = new BossSpawnTracker();

        assertEquals(1, tracker.update(
                List.of(new BossInfo("Keeper", NOW - 50L, 0)),
                NOW
        ).size());

        assertTrue(tracker.update(
                List.of(new BossInfo("Keeper", NOW + 100L, 510)),
                NOW + 200L
        ).isEmpty());

        assertTrue(tracker.update(
                List.of(new BossInfo("Keeper", NOW - 300L, 510)),
                NOW + 300L
        ).isEmpty());
    }

    @Test
    void futureTimerArmsNotificationForNextSpawnCycle() {
        BossSpawnTracker tracker = new BossSpawnTracker();
        BossInfo firstCycle = new BossInfo("Keeper", NOW, 510);
        BossInfo nextCycle = new BossInfo("Keeper", NOW + 60_000L, 510);

        assertEquals(1, tracker.update(List.of(firstCycle), NOW).size());
        assertTrue(tracker.update(List.of(nextCycle), NOW + 1_000L).isEmpty());
        assertEquals(1, tracker.update(List.of(nextCycle), NOW + 60_000L).size());
    }

    @Test
    void differentBossesCanSpawnAtTheSameTime() {
        BossSpawnTracker tracker = new BossSpawnTracker();

        assertEquals(2, tracker.update(
                List.of(
                        new BossInfo("Keeper", NOW, 510),
                        new BossInfo("Harpy", NOW, 520)
                ),
                NOW
        ).size());
    }
}
