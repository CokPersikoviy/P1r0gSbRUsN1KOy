package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.visuals.CompactReload;

@Mixin(Gui.class)
public abstract class VisualsReloadGuiMixin {
   @Inject(method = "setOverlay", at = @At("HEAD"), cancellable = true)
   private void froghelper$detachReload(Overlay overlay, CallbackInfo ci) {
      if (CompactReload.capture(overlay)) {
         ci.cancel();
      }
   }

   @Inject(
      method = "extractRenderState",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V")
   )
   private void froghelper$reloadBar(
      DeltaTracker deltaTracker, boolean shouldRenderLevel, boolean resourcesLoaded, CallbackInfo ci, @Local GuiGraphicsExtractor graphics
   ) {
      CompactReload.render(graphics);
   }
}
