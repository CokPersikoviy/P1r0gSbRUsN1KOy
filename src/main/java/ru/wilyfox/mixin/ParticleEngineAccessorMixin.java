package ru.wilyfox.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TrackingEmitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Queue;

@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessorMixin {
    @Accessor("particles")
    Map<ParticleRenderType, Queue<Particle>> froghelper$getParticles();

    @Accessor("trackingEmitters")
    Queue<TrackingEmitter> froghelper$getTrackingEmitters();

    @Accessor("particlesToAdd")
    Queue<Particle> froghelper$getParticlesToAdd();
}
