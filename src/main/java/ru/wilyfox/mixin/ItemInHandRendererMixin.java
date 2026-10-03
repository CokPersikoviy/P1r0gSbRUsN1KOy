package ru.wilyfox.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.wilyfox.client.hud.config.ConfigManager;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private float froghelper$zeroSwingProgress(float swingProgress, AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float equippedProgress, ItemStack stack) {
        if (ConfigManager.get().render.staticHand) {
            if (stack.is(ItemTags.SWORDS)) {
                return swingProgress;
            }

            return 0.0F;
        }

        return swingProgress;
    }
}
