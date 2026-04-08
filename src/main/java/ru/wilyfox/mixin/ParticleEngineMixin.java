package ru.wilyfox.mixin;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.wilyfox.client.alchemy.AlchemyIngredientTracker;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.fishing.FishingSpotTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
    private static final Set<String> LOGGED_FISHING_PARTICLES = new HashSet<>();

    @Inject(method = "createParticle", at = @At("HEAD"))
    private void froghelper$trackFishingParticles(ParticleOptions particleOptions, double x, double y, double z, double xd, double yd, double zd, CallbackInfoReturnable<?> cir) {
        FishingSpotTracker tracker = FishingSpotTracker.getInstance();
        if (isFishingBubbleParticle(particleOptions)) {
            tracker.addBubble(x, y, z);
            return;
        }

        if (particleOptions.getType() == ParticleTypes.HAPPY_VILLAGER) {
            AlchemyIngredientTracker.getInstance().addParticle(x, y, z);
            return;
        }

        if (tracker.shouldDebugParticles()) {
            logUnknownFishingParticle(tracker.getCurrentFishingLocationId(), particleOptions, x, y, z);
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void froghelper$hideDestroyParticles(BlockPos blockPos, BlockState blockState, CallbackInfo ci) {
        if (ConfigManager.get().render.hideBlockBreakParticles) {
            ci.cancel();
        }
    }

    @Inject(method = "crack", at = @At("HEAD"), cancellable = true)
    private void froghelper$hideCrackParticles(BlockPos blockPos, Direction direction, CallbackInfo ci) {
        if (ConfigManager.get().render.hideBlockBreakParticles) {
            ci.cancel();
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

    private boolean isFishingBubbleParticle(ParticleOptions options) {
        return options.getType() == ParticleTypes.BUBBLE
                || options.getType() == ParticleTypes.BUBBLE_POP
                || options.getType() == ParticleTypes.BUBBLE_COLUMN_UP
                || options.getType() == ParticleTypes.LAVA
                || options.getType() == ParticleTypes.FISHING
                || options.getType() == ParticleTypes.DUST;
    }
}
