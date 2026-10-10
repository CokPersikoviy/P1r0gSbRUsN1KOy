package ru.wilyfox.mixin;

import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LoadingOverlay.class)
public interface VisualsLoadingOverlayAccessor {
   @Accessor("fadeInStart")
   void froghelper$fadeInStart(long var1);

   @Accessor("fadeOutStart")
   long froghelper$fadeOutStart();

   @Accessor("reload")
   ReloadInstance froghelper$reload();
}
