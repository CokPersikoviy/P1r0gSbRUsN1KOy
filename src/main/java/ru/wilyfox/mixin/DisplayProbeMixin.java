package ru.wilyfox.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.profiler.DisplayProbe;

/** Read the extracted list before submitEntities consumes and clears it. */
@Mixin(LevelExtractor.class)
public class DisplayProbeMixin {
    @Inject(method = "extractVisibleEntities", at = @At("RETURN"))
    private void froghelper$sampleDisplays(Camera camera, Frustum frustum, DeltaTracker delta,
                                          LevelRenderState state, CallbackInfo ci) {
        DisplayProbe.onFrame(Minecraft.getInstance(), camera, frustum, state);
    }
}
