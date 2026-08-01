package ru.wilyfox.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.profiler.ProfilerScopeStack;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherProfilerMixin {
    @Unique
    private final ProfilerScopeStack froghelper$entityScopes = new ProfilerScopeStack();

    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void froghelper$beginEntityRender(
            E entity,
            double x,
            double y,
            double z,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        froghelper$entityScopes.push(ModProfiler.getInstance().typedScope(
                "render/entity",
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
        ));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends Entity> void froghelper$endEntityRender(
            E entity,
            double x,
            double y,
            double z,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        froghelper$entityScopes.closeLatest();
    }
}
