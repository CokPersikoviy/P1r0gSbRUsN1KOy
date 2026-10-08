package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.client.visuals.Visuals;

@Mixin(FogRenderer.class)
public abstract class VisualsFogMixin {
    @ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
    private FogData froghelper$color(FogData fog, Camera camera, int distance, DeltaTracker delta, float darken, ClientLevel level) {
        return Visuals.fog(fog, camera.getFluidInCamera());
    }
}
