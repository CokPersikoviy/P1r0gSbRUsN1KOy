package ru.wilyfox.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ru.wilyfox.client.profiler.ModProfiler;
import java.util.IdentityHashMap;
import java.util.Map;
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherProfilerMixin {
    @Unique private static final Map<BlockEntityType<?>, String> froghelper$sectionByType = new IdentityHashMap<>();
    @WrapMethod(method = "submit")
    private void froghelper$profileSubmit(net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera, Operation<Void> original) {
        ModProfiler profiler = ModProfiler.getInstance();
        if (!profiler.isEnabled()) { original.call(state, poseStack, collector, camera); return; }
        String section = froghelper$sectionByType.computeIfAbsent(state.blockEntityType, type -> profiler.typedSection("render/blockEntity", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type)));
        try (ModProfiler.Scope ignored = profiler.scope(section)) { original.call(state, poseStack, collector, camera); }
    }
}
