package ru.wilyfox.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.bridge.AccessoryArmorStandAccess;
import ru.wilyfox.client.clan.ClanSiegeMap;
import ru.wilyfox.client.hud.config.ConfigManager;

/**
 * Hides the local player's own DiamondWorld cosmetic armor stands while in first person (toggle:
 * render.hideFirstPersonCosmetics). Skips their culling render pass so they never draw.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
    private boolean froghelper$hideFirstPersonCosmetics(boolean visible, Entity entity, Frustum frustum,
                                                       double x, double y, double z) {
        // Keep the common path primitive and avoid configuration/location work for unrelated entities.
        if (!visible) return false;
        if (entity instanceof ItemFrame && ClanSiegeMap.shouldHidePhysicalMap(entity)) return false;
        return !(entity instanceof AccessoryArmorStandAccess access
                && ConfigManager.get().render.hideFirstPersonCosmetics
                && Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && access.froghelper$isSelfAccessory());
    }
}
