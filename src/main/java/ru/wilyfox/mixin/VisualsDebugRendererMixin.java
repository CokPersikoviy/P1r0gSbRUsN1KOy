package ru.wilyfox.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.visuals.VisualHitboxes;
import ru.wilyfox.client.hud.config.ConfigManager;

@Mixin(DebugRenderer.class)
public abstract class VisualsDebugRendererMixin {
   @Inject(method = "emitGizmos", at = @At("TAIL"))
   private void froghelper$hitboxes(Frustum frustum, double camX, double camY, double camZ, float partialTicks, CallbackInfo ci) {
      if (ConfigManager.get().visuals.hitboxes.show) {
         if (!Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
            VisualHitboxes.emit(frustum, partialTicks);
         }
      }
   }
}
