package ru.wilyfox.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.profiler.ModProfiler;

@Mixin(Minecraft.class)
public class MinecraftProfilerMixin {
    @Unique
    private ModProfiler.Scope froghelper$frameScope;
    @Unique
    private ModProfiler.Scope froghelper$tickScope;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void froghelper$beginFrame(boolean renderLevel, CallbackInfo ci) {
        froghelper$frameScope = ModProfiler.getInstance().scope("client/runTick");
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void froghelper$endFrame(boolean renderLevel, CallbackInfo ci) {
        froghelper$frameScope.close();
        froghelper$frameScope = null;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void froghelper$beginClientTick(CallbackInfo ci) {
        froghelper$tickScope = ModProfiler.getInstance().scope("client/tick");
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void froghelper$endClientTick(CallbackInfo ci) {
        froghelper$tickScope.close();
        froghelper$tickScope = null;
    }
}
