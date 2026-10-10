package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.client.visuals.Visuals;
import ru.wilyfox.client.hud.config.ConfigManager;

@Mixin(ClientClockManager.class)
public abstract class VisualsClockMixin {
   @ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
   private long froghelper$time(long ticks, Holder<WorldClock> definition) {
      return Visuals.clock(ticks);
   }
}
