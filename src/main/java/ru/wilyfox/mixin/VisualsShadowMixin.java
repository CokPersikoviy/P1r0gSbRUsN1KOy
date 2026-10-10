package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.client.visuals.Visuals;
import ru.wilyfox.client.hud.config.ConfigManager;

@Mixin(EntityRenderer.class)
public abstract class VisualsShadowMixin {
   @WrapOperation(
      method = "extractShadow",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getShadowRadius(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)F"
      )
   )
   private float froghelper$radius(EntityRenderer<?, ?> renderer, EntityRenderState state, Operation<Float> original) {
      return (Float)original.call(renderer, state) * ConfigManager.get().visuals.shadowRadius / 100f;
   }

   @WrapOperation(
      method = "extractShadow",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getShadowStrength(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)F"
      )
   )
   private float froghelper$strength(EntityRenderer<?, ?> renderer, EntityRenderState state, Operation<Float> original) {
      return (Float)original.call(renderer, state) * ConfigManager.get().visuals.shadowStrength / 100f;
   }
}
