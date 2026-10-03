package ru.wilyfox.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.hud.config.ConfigManager;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void froghelper$hideHurtCameraShake(net.minecraft.client.renderer.state.level.CameraRenderState camera, PoseStack poseStack, CallbackInfo ci) {
        if (ConfigManager.get().render.hideHurtCameraShake) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void froghelper$captureHudBlur(CallbackInfo ci) {
        ru.wilyfox.client.hud.widget.HudBlur.captureBeforeGui();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void froghelper$closeHudBlur(CallbackInfo ci) {
        ru.wilyfox.client.hud.widget.HudBlur.close();
    }
}
