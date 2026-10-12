package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.client.hud.config.ConfigManager;

/** Request sprint where vanilla has already checked movement, food, crouching and item use. */
@Mixin(LocalPlayer.class)
public abstract class AutoSprintMixin {
    @Shadow @Final protected Minecraft minecraft;

    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Input;sprint()Z"))
    private boolean froghelper$autoSprint(Input input, Operation<Boolean> original) {
        return original.call(input) || ConfigManager.get().render.autoSprint
                && minecraft.gui.screen() == null && minecraft.isWindowActive();
    }
}
