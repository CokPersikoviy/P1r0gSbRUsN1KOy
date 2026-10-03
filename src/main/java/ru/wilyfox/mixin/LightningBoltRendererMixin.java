package ru.wilyfox.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.hud.config.ConfigManager;

@Mixin(LightningBoltRenderer.class)
public class LightningBoltRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LightningBoltRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void froghelper$hideLightningEffect(LightningBoltRenderState renderState, PoseStack poseStack, SubmitNodeCollector bufferSource, net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
        if (ConfigManager.get().render.hideLightningEffect) {
            ci.cancel();
        }
    }
}
