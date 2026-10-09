package ru.wilyfox.client.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiSoundLimiterTest {
    @Test void limitsABurstToFortyTicksPerSecond() {
        var gate = new UiSoundLimiter(40);
        int allowed = 0;
        for (long millis = 0; millis < 1000; millis++) if (gate.allow(millis * 1_000_000)) allowed++;
        assertEquals(40, allowed);
        assertTrue(gate.allow(1_000_000_000L));
    }

    @Test void rejectedTicksDoNotPostponeTheNextTickOrAccumulate() {
        var gate = new UiSoundLimiter(40);
        assertTrue(gate.allow(0));
        for (int i = 0; i < 100; i++) assertFalse(gate.allow(24_000_000L));
        assertTrue(gate.allow(25_000_000L));
        assertTrue(gate.allow(10_000_000_000L));
        assertFalse(gate.allow(10_000_000_000L));
    }

    @Test void frameJitterDoesNotAccumulateAndRollingWindowsStillHaveAtMostFortyTicks() {
        var gate = new UiSoundLimiter(40);
        var ticks = new java.util.ArrayList<Long>();
        // An 83 Hz caller exposes the same polling jitter at the 40 Hz sound cadence.
        for (long millis = 0; millis < 5000; millis += 12) {
            if (gate.allow(millis * 1_000_000L)) ticks.add(millis);
        }
        assertTrue(ticks.size() >= 190, "Playback accumulated polling jitter: " + ticks.size());
        for (long start : ticks) assertTrue(ticks.stream().filter(t -> t >= start && t < start + 1000).count() <= 40);
    }
}
