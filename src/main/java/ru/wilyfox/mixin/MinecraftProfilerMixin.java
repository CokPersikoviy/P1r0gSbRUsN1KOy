package ru.wilyfox.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.profiler.ProfilerScopeStack;

@Mixin(Minecraft.class)
public class MinecraftProfilerMixin {
    @Unique
    private final ProfilerScopeStack froghelper$frameScopes = new ProfilerScopeStack();
    @Unique
    private final ProfilerScopeStack froghelper$tickScopes = new ProfilerScopeStack();

    @Inject(method = "runTick", at = @At("HEAD"))
    private void froghelper$beginFrame(boolean renderLevel, CallbackInfo ci) {
        froghelper$frameScopes.push(ModProfiler.getInstance().scope("client/runTick"));
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void froghelper$endFrame(boolean renderLevel, CallbackInfo ci) {
        froghelper$frameScopes.closeLatest();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void froghelper$beginClientTick(CallbackInfo ci) {
        froghelper$tickScopes.push(ModProfiler.getInstance().scope("client/tick"));
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void froghelper$endClientTick(CallbackInfo ci) {
        froghelper$tickScopes.closeLatest();
    }
}
