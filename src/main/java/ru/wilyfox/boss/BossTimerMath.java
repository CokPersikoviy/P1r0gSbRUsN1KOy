package ru.wilyfox.boss;

/** EvoPlus adjusts a received raid duration once, before storing its deadline. */
public final class BossTimerMath {
    private BossTimerMath() {
    }

    public static long adjustDurationMillis(long durationMillis, boolean mythicalEvent, boolean raid) {
        return mythicalEvent && raid ? (long) (durationMillis / 1.5D) : durationMillis;
    }
}
