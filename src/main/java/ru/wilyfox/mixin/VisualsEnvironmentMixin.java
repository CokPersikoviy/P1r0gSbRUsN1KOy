package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.client.visuals.Visuals;

@Mixin(EnvironmentAttributeProbe.class)
public abstract class VisualsEnvironmentMixin {
    @ModifyReturnValue(method = "getValue", at = @At("RETURN"))
    private Object froghelper$color(Object original, EnvironmentAttribute<?> attribute, float partialTicks) {
        return Visuals.environmentColor(attribute, original);
    }
}
