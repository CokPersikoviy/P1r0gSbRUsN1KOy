package ru.wilyfox.client.popup;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.miner.ActiveMinerInfo;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinerReturnTrackerTest {
    private static final long RECEIVED_AT_NANOS = TimeUnit.SECONDS.toNanos(100L);

    @Test
    void identicalMinersNotifyOnceEachAsTheirTimersComplete() {
        MinerReturnTracker tracker = new MinerReturnTracker();
        ActiveMinerInfo first = minerWithRemaining(10_000L);
        ActiveMinerInfo second = minerWithRemaining(25_000L);

        tracker.prime(List.of(first, second), RECEIVED_AT_NANOS);

        List<ActiveMinerInfo> firstReturn = tracker.update(
                List.of(first, second),
                RECEIVED_AT_NANOS + TimeUnit.SECONDS.toNanos(10L)
        );
        assertEquals(1, firstReturn.size());

        assertTrue(tracker.update(
                List.of(first, second),
                RECEIVED_AT_NANOS + TimeUnit.SECONDS.toNanos(20L)
        ).isEmpty());

        List<ActiveMinerInfo> secondReturn = tracker.update(
                List.of(first, second),
                RECEIVED_AT_NANOS + TimeUnit.SECONDS.toNanos(25L)
        );
        assertEquals(1, secondReturn.size());

        assertTrue(tracker.update(
                List.of(first, second),
                RECEIVED_AT_NANOS + TimeUnit.SECONDS.toNanos(40L)
        ).isEmpty());
    }

    @Test
    void completeGroupSeenForTheFirstTimeDoesNotGenerateHistoricalNotification() {
        MinerReturnTracker tracker = new MinerReturnTracker();
        ActiveMinerInfo complete = minerWithRemaining(0L);

        assertTrue(tracker.update(List.of(complete), RECEIVED_AT_NANOS).isEmpty());
        assertTrue(tracker.update(List.of(complete), RECEIVED_AT_NANOS).isEmpty());
    }

    private static ActiveMinerInfo minerWithRemaining(long remainingMillis) {
        return new ActiveMinerInfo(
                null,
                3,
                "MOBS",
                2,
                "Mobs",
                "IN_TRAVEL",
                1L,
                RECEIVED_AT_NANOS,
                remainingMillis
        );
    }
}
