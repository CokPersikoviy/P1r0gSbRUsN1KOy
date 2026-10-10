package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.client.visuals.CompactReload;

@Mixin(Minecraft.class)
public abstract class VisualsReloadMinecraftMixin {
   @WrapOperation(
      method = {"reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;", "runTick"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;overlay()Lnet/minecraft/client/gui/screens/Overlay;")
   )
   private Overlay froghelper$pendingReload(Gui gui, Operation<Overlay> original) {
      Overlay overlay = (Overlay)original.call(gui);
      return overlay != null ? overlay : CompactReload.pending();
   }
}
