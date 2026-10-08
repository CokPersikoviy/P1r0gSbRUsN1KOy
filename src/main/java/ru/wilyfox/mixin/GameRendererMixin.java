package ru.wilyfox.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.Projection;
import ru.wilyfox.client.visuals.Visuals;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.hud.config.ConfigManager;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V"))
    private void froghelper$handAspect(Projection projection, float near, float far, float fov, float width, float height, Operation<Void> original) {
        original.call(projection, near, far, fov, Visuals.handWidth(width, height), height);
    }

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
