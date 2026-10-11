package ru.wilyfox.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.hud.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void froghelper$hideFireOverlay(PoseStack poseStack, SubmitNodeCollector bufferSource, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, CallbackInfo ci) {
        var config = ConfigManager.get().render;
        if (config.hideFireOverlay || config.clearLava
                && Minecraft.getInstance().gameRenderer.mainCamera().getFluidInCamera() == FogType.LAVA) {
            ci.cancel();
        }
    }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void froghelper$clearWater(Minecraft client, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        if (ConfigManager.get().render.clearWater) ci.cancel();
    }
}
