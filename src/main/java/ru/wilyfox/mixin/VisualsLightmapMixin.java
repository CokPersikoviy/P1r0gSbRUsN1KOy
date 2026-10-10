package ru.wilyfox.mixin;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.visuals.Visuals;
import ru.wilyfox.client.hud.config.ConfigManager;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class VisualsLightmapMixin {
   @Shadow private boolean needsUpdate;
   @Unique private int froghelper$brightness = -1, froghelper$sky, froghelper$block;
   @Unique private boolean froghelper$skyEnabled, froghelper$blockEnabled;
   @Inject(method = "extract", at = @At("HEAD"))
   private void froghelper$settingsChanged(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
      var c = ConfigManager.get().visuals;
      int brightness = c.brightnessEnabled ? c.brightness : -1;
      if (froghelper$brightness != brightness || froghelper$skyEnabled != c.skyLightEnabled || froghelper$blockEnabled != c.blockLightEnabled
            || c.skyLightEnabled && froghelper$sky != c.skyLightColor || c.blockLightEnabled && froghelper$block != c.blockLightColor) needsUpdate = true;
      froghelper$brightness = brightness; froghelper$skyEnabled = c.skyLightEnabled; froghelper$blockEnabled = c.blockLightEnabled;
      froghelper$sky = c.skyLightColor; froghelper$block = c.blockLightColor;
   }
   @Inject(method = "extract", at = @At("TAIL"))
   private void froghelper$light(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
      if (state.needsUpdate) {
         Visuals.lightmap(state);
      }
   }
}
