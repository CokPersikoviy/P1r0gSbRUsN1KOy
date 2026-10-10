package ru.wilyfox.mixin;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(Options.class)
public abstract class VisualsOptionsMixin {
   @ModifyArg(
      method = "<init>",
      slice = @Slice(
         from = @At(value = "CONSTANT", args = "stringValue=options.fov"),
         to = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;fov:Lnet/minecraft/client/OptionInstance;", opcode = 181)
      ),
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance$IntRange;<init>(II)V"),
      index = 1
   )
   private int froghelper$fovRange(int maxInclusive) {
      return 150;
   }
}
