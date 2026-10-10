package ru.wilyfox.client.hud.fishing;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FishingCastPredictionTest {
    @Test void horizontalLaunchUsesVanillaMeanAndGravityBeforeMovement() {
        var launch = FishingCastPrediction.launch(new Vec3(0, 10, 0), 0, 0);
        assertEquals(0.3, launch.position().z, 1.0E-6);
        assertEquals(1.1, launch.velocity().z, 1.0E-6);
        assertEquals(0.0, launch.velocity().y, 1.0E-6);
        var landing = FishingCastPrediction.predict(launch,
                (position, velocity) -> new FishingCastPrediction.Step(position.add(velocity), true, false));
        assertEquals(9.97, landing.position().y, 1.0E-6);
        assertEquals(1.4, landing.position().z, 1.0E-6);
    }

    @Test void pitchChangesFlightAndWaterIsFirstContact() {
        assertTrue(FishingCastPrediction.launch(Vec3.ZERO, 0, -45).velocity().y > 0);
        assertTrue(FishingCastPrediction.launch(Vec3.ZERO, 0, 45).velocity().y < 0);
        var launch = FishingCastPrediction.launch(new Vec3(0, 3, 0), 0, 30);
        var landing = FishingCastPrediction.predict(launch, (position, velocity) -> {
            Vec3 next = position.add(velocity);
            if (next.y <= 0) {
                Vec3 contact = position.add(velocity.scale(-position.y / velocity.y));
                return new FishingCastPrediction.Step(contact, true, true);
            }
            return new FishingCastPrediction.Step(next, false, false);
        });
        assertNotNull(landing);
        assertTrue(landing.water());
        assertEquals(0, landing.position().y, 1.0E-9);
        assertTrue(landing.position().z > 0.3);
    }

    @Test void missingTerrainAndAirHaveBoundedCostAndNoInventedLanding() {
        var launch = FishingCastPrediction.launch(new Vec3(0, 100, 0), 0, -60);
        assertNull(FishingCastPrediction.predict(launch, (position, velocity) -> null));
        var calls = new AtomicInteger();
        assertNull(FishingCastPrediction.predict(launch, (position, velocity) -> {
            calls.incrementAndGet();
            return new FishingCastPrediction.Step(position.add(velocity), false, false);
        }));
        assertTrue(calls.get() > 0 && calls.get() <= FishingCastPrediction.MAX_STEPS);
        assertNull(FishingCastPrediction.predict(FishingCastPrediction.launch(new Vec3(Double.NaN, 0, 0), 0, 0),
                (position, velocity) -> fail("Invalid cast must not query terrain")));
    }

    @Test void entitySearchBoundsContainEverySimulatedSegment() {
        for (float yaw : new float[]{0, 45, 90, 180}) for (float pitch : new float[]{-80, 0, 30, 80}) {
            var launch = FishingCastPrediction.launch(new Vec3(2, 100, 3), yaw, pitch);
            var bounds = FishingCastPrediction.searchBounds(launch);
            FishingCastPrediction.predict(launch, (start, velocity) -> {
                assertTrue(bounds.contains(start));
                Vec3 end = start.add(velocity);
                assertTrue(bounds.contains(end), "Entity query missed the last bounded movement");
                return new FishingCastPrediction.Step(end, false, false);
            });
        }
    }
}
