package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.client.visuals.Visuals;
import ru.wilyfox.client.hud.config.ConfigManager;

@Mixin(FogRenderer.class)
public abstract class VisualsFogMixin {
    // Both fog distances and fog-colour darkening select from the same environment list.
    @WrapOperation(method = {"computeFogColor", "setupFog"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/environment/FogEnvironment;isApplicable(Lnet/minecraft/world/level/material/FogType;Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean froghelper$antiBlind(FogEnvironment environment, FogType fluid, Entity entity, Operation<Boolean> original) {
        return !(ConfigManager.get().render.antiBlind && environment instanceof BlindnessFogEnvironment)
                && original.call(environment, fluid, entity);
    }

    @ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
    private FogData froghelper$color(FogData fog, Camera camera, int distance, DeltaTracker delta, float darken, ClientLevel level) {
        return Visuals.fog(fog, camera.getFluidInCamera());
    }
}
