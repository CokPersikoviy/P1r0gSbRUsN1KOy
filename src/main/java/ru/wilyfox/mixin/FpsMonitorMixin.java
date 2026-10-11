package ru.wilyfox.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetCatalog;
import ru.wilyfox.client.performance.FpsMonitor;

@Mixin(Minecraft.class)
public abstract class FpsMonitorMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void froghelper$sampleFrame(boolean renderLevel, CallbackInfo ci) {
        var client = (Minecraft) (Object) this;
        // The interval between rendered frames includes swap/vsync and the vanilla FPS limiter.
        boolean active = renderLevel && client.level != null && client.isWindowActive()
                && ConfigManager.isWidgetInCurrentLayout(WidgetCatalog.FPS.key());
        FpsMonitor.INSTANCE.recordFrame(System.nanoTime(), active);
    }
}
