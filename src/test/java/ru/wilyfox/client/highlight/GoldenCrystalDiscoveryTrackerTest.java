package ru.wilyfox.client.highlight;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoldenCrystalDiscoveryTrackerTest {
    @Test
    void reportsPositionOnlyOnFirstObservation() {
        GoldenCrystalDiscoveryTracker tracker = new GoldenCrystalDiscoveryTracker();

        assertEquals(Set.of(42L), tracker.update(Set.of(42L)));
        assertTrue(tracker.update(Set.of(42L)).isEmpty());
    }

    @Test
    void briefModelEngineFlickerDoesNotRepeatNotification() {
        GoldenCrystalDiscoveryTracker tracker = new GoldenCrystalDiscoveryTracker();

        tracker.update(Set.of(42L));
        tracker.update(Set.of());

        assertTrue(tracker.update(Set.of(42L)).isEmpty());
    }

    @Test
    void repeatedlyUnloadedCrystalStaysRememberedUntilLocationChanges() {
        GoldenCrystalDiscoveryTracker tracker = new GoldenCrystalDiscoveryTracker();

        tracker.update(Set.of(42L));
        for (int lap = 0; lap < 100; lap++) {
            for (int scan = 0; scan < 100; scan++) tracker.update(Set.of());
            assertTrue(tracker.update(Set.of(42L)).isEmpty());
        }

        assertEquals(Set.of(43L), tracker.update(Set.of(42L, 43L)));
        tracker.clear();
        assertEquals(Set.of(42L, 43L), tracker.update(Set.of(42L, 43L)));
    }
}
