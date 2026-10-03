package ru.wilyfox.boss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BossTimerMathTest {
    @Test
    void mythicalRaidUsesTheReferenceMultiplier() {
        assertEquals(10_000L, BossTimerMath.adjustDurationMillis(15_000L, true, true));
        assertEquals(10_000L, BossTimerMath.adjustDurationMillis(15_001L, true, true));
    }

    @Test
    void ordinaryBossesAndOrdinaryEventsKeepTheirDuration() {
        assertEquals(15_000L, BossTimerMath.adjustDurationMillis(15_000L, true, false));
        assertEquals(15_000L, BossTimerMath.adjustDurationMillis(15_000L, false, true));
    }

    @Test
    void storedDeadlineCountsDownAtNormalSpeedAfterAdjustment() {
        long receivedAt = 100_000L;
        long deadline = receivedAt + BossTimerMath.adjustDurationMillis(15_000L, true, true);
        assertEquals(10_000L, deadline - receivedAt);
        assertEquals(5_000L, deadline - (receivedAt + 5_000L));
        assertEquals(0L, deadline - (receivedAt + 10_000L));
    }
}
