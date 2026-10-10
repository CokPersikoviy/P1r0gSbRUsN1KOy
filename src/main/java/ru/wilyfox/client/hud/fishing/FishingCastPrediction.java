package ru.wilyfox.client.hud.fishing;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Mean vanilla 26.2 launch, without consuming randomness or creating a world entity. */
final class FishingCastPrediction {
    static final int MAX_STEPS = 120;
    private static final double MAX_DISTANCE_SQUARED = 32.0 * 32.0;

    private FishingCastPrediction() { }

    static Launch launch(Vec3 eye, float yaw, float pitch) {
        float yawRadians = -yaw * Mth.DEG_TO_RAD - Mth.PI;
        float pitchRadians = -pitch * Mth.DEG_TO_RAD;
        float cosYaw = Mth.cos(yawRadians);
        float sinYaw = Mth.sin(yawRadians);
        float cosPitch = -Mth.cos(pitchRadians);
        float sinPitch = Mth.sin(pitchRadians);
        Vec3 start = eye.add(-sinYaw * 0.3, 0.0, -cosYaw * 0.3);
        Vec3 direction = new Vec3(-sinYaw, Mth.clamp(-sinPitch / cosPitch, -5F, 5F), -cosYaw);
        // FishingHook uses component-wise triangle(0.5, 0.0103365) noise: its mean is 0.5.
        Vec3 velocity = direction.scale(0.6 / direction.length() + 0.5);
        return new Launch(start, velocity);
    }

    static Landing predict(Launch launch, CollisionProbe world) {
        if (!launch.position.isFinite() || !launch.velocity.isFinite()) return null;
        Vec3 position = launch.position;
        Vec3 velocity = launch.velocity;
        for (int step = 0; step < MAX_STEPS; step++) {
            velocity = velocity.add(0.0, -0.03, 0.0);
            Step result = world.move(position, velocity);
            if (result == null) return null; // Unloaded terrain or world boundary.
            if (result.landed) return new Landing(result.position, result.water);
            position = result.position;
            if (position.distanceToSqr(launch.position) > MAX_DISTANCE_SQUARED) return null;
            velocity = velocity.scale(0.92);
        }
        return null;
    }

    /** Query entity sections once for the whole bounded arc, including the last movement. */
    static AABB searchBounds(Launch launch) {
        Vec3 position = launch.position;
        Vec3 velocity = launch.velocity;
        AABB bounds = new AABB(position, position);
        for (int step = 0; step < MAX_STEPS; step++) {
            velocity = velocity.add(0.0, -0.03, 0.0);
            position = position.add(velocity);
            bounds = bounds.minmax(new AABB(position, position));
            if (position.distanceToSqr(launch.position) > MAX_DISTANCE_SQUARED) break;
            velocity = velocity.scale(0.92);
        }
        // Matches the hook's swept box plus ProjectileUtil's one-block search padding.
        return bounds.inflate(1.125);
    }

    @FunctionalInterface
    interface CollisionProbe { Step move(Vec3 position, Vec3 velocity); }
    record Launch(Vec3 position, Vec3 velocity) { }
    record Step(Vec3 position, boolean landed, boolean water) { }
    record Landing(Vec3 position, boolean water) { }
}
