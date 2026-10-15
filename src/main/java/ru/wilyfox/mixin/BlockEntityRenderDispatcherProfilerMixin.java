package ru.wilyfox.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.profiler.ProfilerScopeStack;
import java.util.IdentityHashMap;
import java.util.Map;
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherProfilerMixin {
    @Unique private static final Map<BlockEntityType<?>, String> froghelper$sectionByType = new IdentityHashMap<>();
    @Unique private final ProfilerScopeStack froghelper$submitScopes = new ProfilerScopeStack();
    @Inject(method = "submit", at = @At("HEAD"))
    private void froghelper$beginSubmit(net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
        ModProfiler profiler = ModProfiler.getInstance();
        if (!profiler.isEnabled()) return;
        String section = froghelper$sectionByType.computeIfAbsent(state.blockEntityType, type -> profiler.typedSection("render/blockEntity", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type)));
        froghelper$submitScopes.push(profiler.scope(section));
    }
    @Inject(method = "submit", at = @At("RETURN"))
    private void froghelper$endSubmit(CallbackInfo ci) {
        froghelper$submitScopes.closeLatest();
    }
    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/CrashReport;forThrowable(Ljava/lang/Throwable;Ljava/lang/String;)Lnet/minecraft/CrashReport;"))
    private void froghelper$failedSubmit(CallbackInfo ci) {
        froghelper$submitScopes.closeLatest();
    }
}
