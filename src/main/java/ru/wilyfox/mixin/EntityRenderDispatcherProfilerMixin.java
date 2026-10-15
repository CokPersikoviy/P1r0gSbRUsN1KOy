package ru.wilyfox.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.profiler.ProfilerScopeStack;
import java.util.IdentityHashMap;
import java.util.Map;
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherProfilerMixin {
    @Unique private static final Map<EntityType<?>, String> froghelper$sectionByType = new IdentityHashMap<>();
    @Unique private final ProfilerScopeStack froghelper$submitScopes = new ProfilerScopeStack();
    @Inject(method = "submit", at = @At("HEAD"))
    private void froghelper$beginSubmit(net.minecraft.client.renderer.entity.state.EntityRenderState state, net.minecraft.client.renderer.state.level.CameraRenderState camera, double x, double y, double z, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, CallbackInfo ci) {
        ModProfiler profiler = ModProfiler.getInstance();
        if (!profiler.isEnabled()) return;
        String section = froghelper$sectionByType.computeIfAbsent(state.entityType, type -> profiler.typedSection("render/entity", BuiltInRegistries.ENTITY_TYPE.getKey(type)));
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
