package ru.wilyfox.mixin;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.wilyfox.client.hud.fishing.FishingParticleTypes;
import ru.wilyfox.client.hud.fishing.FishingSpotTracker;
import ru.wilyfox.client.profiler.ModProfiler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;


import java.util.HashSet;
import java.util.Set;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
    private static final Set<String> LOGGED_FISHING_PARTICLES = new HashSet<>();
    @WrapMethod(method = "tick")
    private void froghelper$particleTick(Operation<Void> original) {
        try (var ignored = ModProfiler.getInstance().scope("render/particles/tick")) {
            original.call();
        }
    }

    @WrapMethod(method = "extract")
    private void froghelper$particleFrame(ParticlesRenderState state, Frustum frustum, Camera camera,
                                         float partialTick, Operation<Void> original) {
        try (var ignored = ModProfiler.getInstance().scope("render/particles/frame")) {
            original.call(state, frustum, camera, partialTick);
        }
    }

    @Inject(method = "createParticle", at = @At("HEAD"))
    private void froghelper$trackFishingParticles(ParticleOptions particleOptions, double x, double y, double z, double xd, double yd, double zd, CallbackInfoReturnable<?> cir) {
        FishingSpotTracker tracker = FishingSpotTracker.getInstance();
        if (tracker.shouldDebugParticles() && !FishingParticleTypes.isFishingSpot(particleOptions)) {
            logUnknownFishingParticle(tracker.getCurrentFishingLocationId(), particleOptions, x, y, z);
        }
    }

    private void logUnknownFishingParticle(String locationId, ParticleOptions options, double x, double y, double z) {
        if (locationId == null || options == null || options.getType() == null) {
            return;
        }

        String particleKey = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).toString();
        String logKey = locationId + "|" + particleKey;
        if (!LOGGED_FISHING_PARTICLES.add(logKey)) {
            return;
        }

        info(
                LOGGER,
                "Fishing particle debug: location={}, particle={}, options={}, pos=({}, {}, {})",
                locationId,
                particleKey,
                options,
                String.format(java.util.Locale.US, "%.3f", x),
                String.format(java.util.Locale.US, "%.3f", y),
                String.format(java.util.Locale.US, "%.3f", z)
        );
    }

}
