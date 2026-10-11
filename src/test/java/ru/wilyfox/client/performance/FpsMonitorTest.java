package ru.wilyfox.client.performance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FpsMonitorTest {
    @Test void averagesFrameTimesAndTheSlowestFractions() {
        var monitor = new FpsMonitor();
        long now = 1;
        monitor.recordFrame(now, true);
        for (int i = 0; i < 1000; i++) {
            now += i < 990 ? 10_000_000L : i < 999 ? 50_000_000L : 200_000_000L;
            monitor.recordFrame(now, true);
        }
        var stats = monitor.snapshot(now);
        assertEquals(1000, stats.sampleCount());
        assertEquals(1000 / 10.55, stats.average(), .00001);
        assertEquals(10 / .65, stats.onePercentLow(), .00001);
        assertEquals(5, stats.pointOnePercentLow(), .00001);
    }

    @Test void includesLongStallsAndExpiresOldFrames() {
        var monitor = new FpsMonitor();
        monitor.recordFrame(0, true);
        monitor.recordFrame(10_000_000L, true);
        monitor.recordFrame(2_010_000_000L, true);
        var stats = monitor.snapshot(2_010_000_000L);
        assertEquals(.5, stats.pointOnePercentLow());
        assertEquals(2 / 2.01, stats.average(), .00001);
        assertEquals(1, monitor.snapshot(30_010_000_000L).sampleCount());
        assertEquals(0, monitor.snapshot(32_010_000_000L).sampleCount());
    }

    @Test void boundsTheRingAndKeepsTheNewestSamples() {
        var monitor = new FpsMonitor();
        monitor.recordFrame(0, true);
        monitor.recordFrame(100_000_000L, true);
        long now = 100_000_000L;
        for (int i = 0; i < FpsMonitor.CAPACITY + 7; i++) monitor.recordFrame(now += 1_000_000L, true);
        var stats = monitor.snapshot(now);
        assertEquals(FpsMonitor.CAPACITY, stats.sampleCount());
        assertEquals(1000, stats.average());
        assertEquals(1000, stats.pointOnePercentLow());
    }

    @Test void throttlesAggregationButResetsWhenInactive() {
        var monitor = new FpsMonitor();
        monitor.recordFrame(0, true);
        assertTrue(Double.isNaN(monitor.snapshot(0).average()));
        monitor.recordFrame(10_000_000L, true);
        var first = monitor.snapshot(10_000_000L);
        monitor.recordFrame(20_000_000L, true);
        assertSame(first, monitor.snapshot(20_000_000L));
        assertNotSame(first, monitor.snapshot(510_000_000L));
        monitor.recordFrame(1_000_000_000L, false);
        assertEquals(0, monitor.snapshot(1_000_000_000L).sampleCount());
        monitor.recordFrame(10_000_000_000L, true);
        monitor.recordFrame(10_010_000_000L, true);
        assertEquals(100, monitor.snapshot(10_010_000_000L).average());
        monitor.recordFrame(10_010_000_000L, true);
        assertEquals(0, monitor.snapshot(10_010_000_000L).sampleCount());
    }
}
