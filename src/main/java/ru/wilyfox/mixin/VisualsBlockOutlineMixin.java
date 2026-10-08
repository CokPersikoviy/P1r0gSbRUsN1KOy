package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.client.hud.config.ConfigManager;

@Mixin(LevelRenderer.class)
public abstract class VisualsBlockOutlineMixin {
    @WrapOperation(method = "submitBlockOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;submitHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IFZ)V", ordinal = 0))
    private void froghelper$contrast(LevelRenderer renderer, PoseStack pose, SubmitNodeCollector collector, RenderType type, BlockOutlineRenderState state, int color, float width, boolean translucent, Operation<Void> original) {
        if (!ConfigManager.get().visuals.blockOutline) original.call(renderer, pose, collector, type, state, color, width, translucent);
    }

    @WrapOperation(method = "submitBlockOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;submitHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IFZ)V", ordinal = 1))
    private void froghelper$outline(LevelRenderer renderer, PoseStack pose, SubmitNodeCollector collector, RenderType type, BlockOutlineRenderState state, int color, float width, boolean translucent, Operation<Void> original) {
        var config = ConfigManager.get().visuals;
        original.call(renderer, pose, collector, type, state,
                config.blockOutline ? config.blockOutlineColor | 0xFF000000 : color,
                config.blockOutline ? (float) Math.clamp(config.blockOutlineWidth, 1, 8) : width, translucent);
    }
}
