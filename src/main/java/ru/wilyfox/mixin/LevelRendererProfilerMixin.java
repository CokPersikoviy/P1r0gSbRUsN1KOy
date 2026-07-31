package ru.wilyfox.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.profiler.ModProfiler;

@Mixin(LevelRenderer.class)
public class LevelRendererProfilerMixin {
    @Unique
    private ModProfiler.Scope froghelper$worldRenderScope;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void froghelper$beginWorldRender(CallbackInfo ci) {
        froghelper$worldRenderScope = ModProfiler.getInstance().scope("render/world");
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void froghelper$endWorldRender(CallbackInfo ci) {
        froghelper$worldRenderScope.close();
        froghelper$worldRenderScope = null;
    }
}
