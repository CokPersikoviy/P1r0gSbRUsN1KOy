package ru.wilyfox.mixin;

import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.hud.internal.WidgetLayoutAnimation;

@Mixin(TextureManager.class)
public class WidgetTextureReloadMixin {
    @Inject(method = {"lambda$reload$2", "close"}, at = @At("HEAD"))
    private void froghelper$invalidateSnapshots(CallbackInfo ci) { WidgetLayoutAnimation.invalidateResources(); }
}
