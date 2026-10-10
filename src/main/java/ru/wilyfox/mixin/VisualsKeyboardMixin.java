package ru.wilyfox.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.visuals.VisualsTab;

@Mixin(KeyboardHandler.class)
public abstract class VisualsKeyboardMixin {
   @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
   private void froghelper$tabCursor(long handle, int action, KeyEvent event, CallbackInfo ci) {
      if (action == 1 && VisualsTab.press(false, event.key())) {
         ci.cancel();
      }
   }
}
