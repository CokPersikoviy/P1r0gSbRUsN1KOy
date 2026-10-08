package ru.wilyfox.client.hud.fishing;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingSpotTrackerTest {
    @Test
    void nearbyBubblesProduceOneWeightedCluster() {
        List<FishingSpot> spots = FishingSpotTracker.clusterBubbles(List.of(
                bubble(10.10D, 64.0D, 20.10D, 1_000L),
                bubble(10.30D, 64.0D, 20.20D, 1_100L),
                bubble(10.55D, 64.0D, 20.25D, 1_200L),
                bubble(10.70D, 64.0D, 20.40D, 1_300L)
        ));

        assertEquals(1, spots.size());
        FishingSpot spot = spots.getFirst();
        assertEquals(4, spot.bubbleCount());
        assertEquals(10.4125D, spot.center().x, 0.0001D);
        assertEquals(20.2375D, spot.center().z, 0.0001D);
        assertEquals(1_300L, spot.latestTimestamp());
    }

    @Test
    void distantBubbleGroupsRemainSeparate() {
        List<FishingSpot> spots = FishingSpotTracker.clusterBubbles(List.of(
                bubble(0.10D, 64.0D, 0.10D, 1_000L),
                bubble(0.20D, 64.0D, 0.20D, 1_010L),
                bubble(0.30D, 64.0D, 0.30D, 1_020L),
                bubble(8.10D, 64.0D, 8.10D, 2_000L),
                bubble(8.20D, 64.0D, 8.20D, 2_010L),
                bubble(8.30D, 64.0D, 8.30D, 2_020L)
        ));

        assertEquals(2, spots.size());
        assertTrue(spots.stream().allMatch(spot -> spot.bubbleCount() == 3));
    }

    @Test
    void isolatedServerSignalsStillProduceMarkers() {
        List<FishingSpot> spots = FishingSpotTracker.clusterBubbles(List.of(
                bubble(0.0D, 64.0D, 0.0D, 1_000L),
                bubble(5.0D, 64.0D, 5.0D, 1_100L),
                bubble(10.0D, 64.0D, 10.0D, 1_200L)
        ));

        assertEquals(3, spots.size());
        assertTrue(spots.stream().allMatch(spot -> spot.bubbleCount() == 1));
    }

    @Test
    void packetStrengthWeightsTheClusterWithoutCreatingAnEntryPerParticle() {
        var spots = FishingSpotTracker.clusterBubbles(List.of(
                new FishingBubbleEntry(new Vec3(0.1D, 64D, 0.1D), 1_000L, 5),
                new FishingBubbleEntry(new Vec3(0.3D, 64D, 0.3D), 1_100L, 15)));
        assertEquals(1, spots.size());
        assertEquals(20, spots.getFirst().bubbleCount());
        assertEquals(0.25D, spots.getFirst().center().x, 0.0001D);
        assertEquals(1_100L, spots.getFirst().latestTimestamp());
    }

    private static FishingBubbleEntry bubble(double x, double y, double z, long timestamp) {
        return new FishingBubbleEntry(new Vec3(x, y, z), timestamp);
    }
}
