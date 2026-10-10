package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.visuals.VisualsCamera;
import ru.wilyfox.client.visuals.VisualsTab;

@Mixin(MouseHandler.class)
public abstract class VisualsMouseMixin {
   @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
   private void froghelper$freelook(LocalPlayer player, double dx, double dy, Operation<Void> original) {
      if (VisualsCamera.active()) {
         VisualsCamera.turn(dx, dy);
      } else {
         original.call(player, dx, dy);
      }
   }

   @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
   private void froghelper$tabPages(long handle, double xoffset, double yoffset, CallbackInfo ci) {
      if (VisualsTab.scroll(yoffset != 0.0 ? yoffset : -xoffset)) {
         ci.cancel();
      }
   }

   @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
   private void froghelper$tabCursor(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
      if (action == 1 && VisualsTab.press(true, rawButtonInfo.button())) {
         ci.cancel();
      }
   }
}
