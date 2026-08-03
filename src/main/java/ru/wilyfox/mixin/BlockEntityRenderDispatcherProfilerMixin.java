package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ru.wilyfox.client.profiler.ModProfiler;

import java.util.IdentityHashMap;
import java.util.Map;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherProfilerMixin {
    @Unique
    private static final Map<BlockEntityType<?>, String> froghelper$sectionByType = new IdentityHashMap<>();

    @WrapMethod(method = "render")
    private <E extends BlockEntity> void froghelper$profileBlockEntityRender(
            E blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Operation<Void> original
    ) {
        ModProfiler profiler = ModProfiler.getInstance();
        if (!profiler.isEnabled()) {
            original.call(blockEntity, partialTick, poseStack, bufferSource);
            return;
        }

        String section = froghelper$sectionByType.computeIfAbsent(
                blockEntity.getType(),
                type -> profiler.typedSection("render/blockEntity", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type))
        );
        try (ModProfiler.Scope ignored = profiler.scope(section)) {
            original.call(blockEntity, partialTick, poseStack, bufferSource);
        }
    }
}
