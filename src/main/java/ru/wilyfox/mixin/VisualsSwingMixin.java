package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.client.visuals.VisualsHand;

@Mixin(LivingEntity.class)
public abstract class VisualsSwingMixin {
   @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
   private int froghelper$swing(int duration) {
      return VisualsHand.swing((LivingEntity)(Object)this, duration);
   }
}
