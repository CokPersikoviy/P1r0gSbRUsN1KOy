package ru.wilyfox.mixin;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.client.clan.PlayerClanNameFormatter;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.moduser.ModUserBadge;
import ru.wilyfox.client.moduser.ModUserStorage;
@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void froghelper$highlightTargetNameTag(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (state.nameTag == null) return;
        String name = avatar.getScoreboardName();
        state.nameTag = PlayerClanNameFormatter.apply(state.nameTag, name);
        if (ConfigManager.get().render.modUserBadge && ModUserStorage.isKnown(name)) state.nameTag = ModUserBadge.prefix(state.nameTag);
    }
}
