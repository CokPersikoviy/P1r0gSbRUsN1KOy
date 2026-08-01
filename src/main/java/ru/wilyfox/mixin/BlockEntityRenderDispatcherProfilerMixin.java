package ru.wilyfox.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.profiler.ProfilerScopeStack;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherProfilerMixin {
    @Unique
    private final ProfilerScopeStack froghelper$blockEntityScopes = new ProfilerScopeStack();

    @Inject(method = "render", at = @At("HEAD"))
    private <E extends BlockEntity> void froghelper$beginBlockEntityRender(
            E blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci
    ) {
        froghelper$blockEntityScopes.push(ModProfiler.getInstance().typedScope(
                "render/blockEntity",
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType())
        ));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends BlockEntity> void froghelper$endBlockEntityRender(
            E blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci
    ) {
        froghelper$blockEntityScopes.closeLatest();
    }
}
