package ru.wilyfox.mixin;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.utility.PlayerNameFormatter;
@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void froghelper$highlightTargetNameTag(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (state.nameTag == null || !(avatar instanceof Player player)) return;
        String name = player.getGameProfile().name();
        state.nameTag = PlayerNameFormatter.apply(state.nameTag, name);
    }
}
