package ru.wilyfox.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
   @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
   private float froghelper$equip(float inverseArmHeight) {
      return ru.wilyfox.client.visuals.VisualsHand.equip(inverseArmHeight);
   }

   @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = Shift.AFTER))
   private void froghelper$transform(
      AbstractClientPlayer player,
      float frameInterp,
      float xRot,
      InteractionHand hand,
      float attack,
      ItemStack itemStack,
      float inverseArmHeight,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      int lightCoords,
      CallbackInfo ci
   ) {
      ru.wilyfox.client.visuals.VisualsHand.apply(poseStack, hand);
   }

   @Inject(
      method = "submitArmWithItem",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
      )
   )
   private void froghelper$scale(
      AbstractClientPlayer player,
      float frameInterp,
      float xRot,
      InteractionHand hand,
      float attack,
      ItemStack itemStack,
      float inverseArmHeight,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      int lightCoords,
      CallbackInfo ci
   ) {
      ru.wilyfox.client.visuals.VisualsHand.scale(poseStack, hand);
   }
}
