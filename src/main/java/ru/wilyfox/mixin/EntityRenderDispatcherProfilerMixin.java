package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ru.wilyfox.client.profiler.ModProfiler;

import java.util.IdentityHashMap;
import java.util.Map;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherProfilerMixin {
    @Unique
    private static final Map<EntityType<?>, String> froghelper$sectionByType = new IdentityHashMap<>();

    @WrapMethod(method = "render")
    private <E extends Entity> void froghelper$profileEntityRender(
            E entity,
            double x,
            double y,
            double z,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            Operation<Void> original
    ) {
        ModProfiler profiler = ModProfiler.getInstance();
        if (!profiler.isEnabled()) {
            original.call(entity, x, y, z, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        String section = froghelper$sectionByType.computeIfAbsent(
                entity.getType(),
                type -> profiler.typedSection("render/entity", BuiltInRegistries.ENTITY_TYPE.getKey(type))
        );
        try (ModProfiler.Scope ignored = profiler.scope(section)) {
            original.call(entity, x, y, z, partialTick, poseStack, bufferSource, packedLight);
        }
    }
}
