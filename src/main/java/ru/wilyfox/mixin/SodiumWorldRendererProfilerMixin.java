package ru.wilyfox.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.profiler.ProfilerScopeStack;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public class SodiumWorldRendererProfilerMixin {
    @Unique
    private final ProfilerScopeStack froghelper$sodiumBlockEntitiesScopes = new ProfilerScopeStack();
    @Unique
    private static final ProfilerScopeStack froghelper$sodiumBlockEntityScopes = new ProfilerScopeStack();

    @Inject(method = "renderBlockEntities", at = @At("HEAD"), require = 0, remap = false)
    private void froghelper$beginSodiumBlockEntities(CallbackInfo ci) {
        froghelper$sodiumBlockEntitiesScopes.push(ModProfiler.getInstance().scope("render/sodium/blockEntities"));
    }

    @Inject(method = "renderBlockEntities", at = @At("RETURN"), require = 0, remap = false)
    private void froghelper$endSodiumBlockEntities(CallbackInfo ci) {
        froghelper$sodiumBlockEntitiesScopes.closeLatest();
    }

    @Inject(method = "renderBlockEntity", at = @At("HEAD"), require = 0, remap = false)
    private static void froghelper$beginSodiumBlockEntity(CallbackInfo ci) {
        froghelper$sodiumBlockEntityScopes.push(ModProfiler.getInstance().scope("render/sodium/blockEntity"));
    }

    @Inject(method = "renderBlockEntity", at = @At("RETURN"), require = 0, remap = false)
    private static void froghelper$endSodiumBlockEntity(CallbackInfo ci) {
        froghelper$sodiumBlockEntityScopes.closeLatest();
    }
}
