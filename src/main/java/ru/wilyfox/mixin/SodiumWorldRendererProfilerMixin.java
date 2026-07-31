package ru.wilyfox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.profiler.ModProfiler;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public class SodiumWorldRendererProfilerMixin {
    @Unique
    private ModProfiler.Scope froghelper$sodiumBlockEntitiesScope;
    @Unique
    private ModProfiler.Scope froghelper$sodiumBlockEntityScope;

    @Inject(method = "renderBlockEntities", at = @At("HEAD"), require = 0, remap = false)
    private void froghelper$beginSodiumBlockEntities(CallbackInfo ci) {
        froghelper$sodiumBlockEntitiesScope = ModProfiler.getInstance().scope("render/sodium/blockEntities");
    }

    @Inject(method = "renderBlockEntities", at = @At("RETURN"), require = 0, remap = false)
    private void froghelper$endSodiumBlockEntities(CallbackInfo ci) {
        froghelper$sodiumBlockEntitiesScope.close();
        froghelper$sodiumBlockEntitiesScope = null;
    }

    @Inject(method = "renderBlockEntity", at = @At("HEAD"), require = 0, remap = false)
    private void froghelper$beginSodiumBlockEntity(CallbackInfo ci) {
        froghelper$sodiumBlockEntityScope = ModProfiler.getInstance().scope("render/sodium/blockEntity");
    }

    @Inject(method = "renderBlockEntity", at = @At("RETURN"), require = 0, remap = false)
    private void froghelper$endSodiumBlockEntity(CallbackInfo ci) {
        froghelper$sodiumBlockEntityScope.close();
        froghelper$sodiumBlockEntityScope = null;
    }
}
