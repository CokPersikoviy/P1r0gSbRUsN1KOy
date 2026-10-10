package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.client.visuals.Visuals;
import ru.wilyfox.client.hud.config.ConfigManager;

@Mixin(Level.class)
public abstract class VisualsWeatherMixin {
   @ModifyReturnValue(method = "getRainLevel", at = @At("RETURN"))
   private float froghelper$rain(float value) {
      return (Object)this instanceof ClientLevel ? Visuals.rain(value) : value;
   }

   @ModifyReturnValue(method = "getThunderLevel", at = @At("RETURN"))
   private float froghelper$thunder(float value) {
      return (Object)this instanceof ClientLevel ? Visuals.thunder(value) : value;
   }
}
