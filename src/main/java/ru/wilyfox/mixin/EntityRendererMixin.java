package ru.wilyfox.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.wilyfox.bridge.AccessoryArmorStandAccess;
import ru.wilyfox.client.hud.config.ConfigManager;

/**
 * Hides the local player's own DiamondWorld cosmetic armor stands while in first person (toggle:
 * render.hideFirstPersonCosmetics). Skips their culling render pass so they never draw.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void froghelper$hideFirstPersonCosmetics(Entity entity, Frustum frustum, double x, double y, double z,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (!ConfigManager.get().render.hideFirstPersonCosmetics) {
            return;
        }
        if (entity instanceof AccessoryArmorStandAccess access
                && access.froghelper$isSelfAccessory()
                && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            cir.setReturnValue(false);
        }
    }
}
