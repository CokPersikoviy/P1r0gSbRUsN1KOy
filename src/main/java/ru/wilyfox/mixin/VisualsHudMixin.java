package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.visuals.VisualCrosshair;
import ru.wilyfox.client.visuals.VisualsCamera;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.visuals.VisualsTab;

@Mixin(Hud.class)
public abstract class VisualsHudMixin {
   @WrapOperation(
      method = "extractCrosshair",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
         ordinal = 0
      )
   )
   private void froghelper$crosshair(
      GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, Operation<Void> original
   ) {
      if (ConfigManager.get().visuals.crosshair.enabled) {
         VisualCrosshair.render(graphics);
      } else {
         original.call(graphics, pipeline, sprite, x, y, width, height);
      }
   }

   @WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
   private boolean froghelper$crosshairInThirdPerson(CameraType type, Operation<Boolean> original) {
      return VisualsCamera.active() ? false : (Boolean)original.call(type) || ConfigManager.get().visuals.crosshair.enabled && ConfigManager.get().visuals.crosshair.thirdPerson;
   }

   @WrapOperation(method = "extractTabList", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
   private boolean froghelper$keepTab(KeyMapping key, Operation<Boolean> original) {
      return (Boolean)original.call(key) || VisualsTab.interactive();
   }
}
