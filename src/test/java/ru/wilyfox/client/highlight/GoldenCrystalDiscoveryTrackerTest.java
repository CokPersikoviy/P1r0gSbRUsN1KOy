package ru.wilyfox.client.highlight;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoldenCrystalDiscoveryTrackerTest {
    @Test
    void reportsPositionOnlyOnFirstObservation() {
        GoldenCrystalDiscoveryTracker tracker = new GoldenCrystalDiscoveryTracker(100L);

        assertEquals(Set.of(42L), tracker.update(Set.of(42L), 10L));
        assertTrue(tracker.update(Set.of(42L), 20L).isEmpty());
    }

    @Test
    void briefModelEngineFlickerDoesNotRepeatNotification() {
        GoldenCrystalDiscoveryTracker tracker = new GoldenCrystalDiscoveryTracker(100L);

        tracker.update(Set.of(42L), 10L);
        tracker.update(Set.of(), 20L);

        assertTrue(tracker.update(Set.of(42L), 30L).isEmpty());
    }

    @Test
    void positionCanBeReportedAgainAfterItWasGoneLongEnough() {
        GoldenCrystalDiscoveryTracker tracker = new GoldenCrystalDiscoveryTracker(100L);

        tracker.update(Set.of(42L), 10L);
        tracker.update(Set.of(), 111L);

        assertEquals(Set.of(42L), tracker.update(Set.of(42L), 112L));
    }
}
