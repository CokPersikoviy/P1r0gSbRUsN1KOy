package ru.wilyfox.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.profiler.ProfilerScopeStack;

@Mixin(LevelRenderer.class)
public class LevelRendererProfilerMixin {
    @Unique
    private final ProfilerScopeStack froghelper$worldRenderScopes = new ProfilerScopeStack();

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void froghelper$beginWorldRender(CallbackInfo ci) {
        froghelper$worldRenderScopes.push(ModProfiler.getInstance().scope("render/world"));
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void froghelper$endWorldRender(CallbackInfo ci) {
        froghelper$worldRenderScopes.closeLatest();
    }
}
