package ru.wilyfox.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.visuals.VisualHitboxes;
import ru.wilyfox.client.hud.config.ConfigManager;

@Mixin(EntityHitboxDebugRenderer.class)
public abstract class VisualsEntityHitboxMixin {
   @Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
   private void froghelper$style(double camX, double camY, double camZ, DebugValueAccess debugValues, Frustum frustum, float partialTicks, CallbackInfo ci) {
      if (ConfigManager.get().visuals.hitboxes.custom || ConfigManager.get().visuals.hitboxes.show) {
         VisualHitboxes.emit(frustum, partialTicks);
         ci.cancel();
      }
   }
}
