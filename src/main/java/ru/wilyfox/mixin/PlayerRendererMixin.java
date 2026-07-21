package ru.wilyfox.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.wilyfox.client.clan.PlayerClanNameFormatter;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.moduser.ModUserBadge;
import ru.wilyfox.client.moduser.ModUserStorage;
import ru.wilyfox.client.target.TargetListStore;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @ModifyVariable(
            method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Component froghelper$highlightTargetNameTag(Component component, PlayerRenderState state, Component originalComponent, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Component base = PlayerClanNameFormatter.apply(component, state.name);

        if (TargetListStore.isTarget(state.name)) {
            base = base.copy().withStyle(style -> style.withColor(0xFF3030).withBold(true));
        }

        if (ConfigManager.get().render.modUserBadge && ModUserStorage.isKnown(state.name)) {
            base = ModUserBadge.prefix(base);
        }

        return base;
    }
}
