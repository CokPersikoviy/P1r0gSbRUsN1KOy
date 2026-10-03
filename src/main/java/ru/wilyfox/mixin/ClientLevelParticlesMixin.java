package ru.wilyfox.mixin;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.hud.config.ConfigManager;
@Mixin(ClientLevel.class)
public class ClientLevelParticlesMixin {
    @Inject(method = {"addDestroyBlockEffect", "addBreakingBlockEffect"}, at = @At("HEAD"), cancellable = true)
    private void froghelper$hideBlockParticles(CallbackInfo ci) {
        if (ConfigManager.get().render.hideBlockBreakParticles) ci.cancel();
    }
}
