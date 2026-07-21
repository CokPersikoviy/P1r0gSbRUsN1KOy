package ru.wilyfox.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.wilyfox.bridge.AccessoryArmorStandAccess;

/**
 * Flags a DiamondWorld cosmetic armor stand as "the local player's accessory" when its head item
 * carries the accessory-owner tag for this player. {@code EntityRendererMixin} then hides such stands
 * in first person. Owner is read from the item's custom data ({@code PublicBukkitValues:
 * diamondworld:accessory_owner}, or the legacy {@code accessory_owner}).
 */
@Mixin(ArmorStand.class)
public abstract class ArmorStandMixin implements AccessoryArmorStandAccess {
    @Unique
    private boolean froghelper$selfAccessory;

    @Inject(method = "setItemSlot", at = @At("TAIL"))
    private void froghelper$markSelfAccessory(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if (slot != EquipmentSlot.HEAD || froghelper$selfAccessory) {
            return;
        }

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        String name = minecraft.player.getGameProfile().getName();
        if (froghelper$isAccessoryOwner(data.getUnsafe(), name)) {
            froghelper$selfAccessory = true;
        }
    }

    @Unique
    private static boolean froghelper$isAccessoryOwner(CompoundTag tag, String name) {
        CompoundTag bukkit = tag.getCompound("PublicBukkitValues");
        if (bukkit.contains("diamondworld:accessory_owner")
                && bukkit.getString("diamondworld:accessory_owner").equalsIgnoreCase(name)) {
            return true;
        }
        return tag.contains("accessory_owner") && tag.getString("accessory_owner").equalsIgnoreCase(name);
    }

    @Override
    public boolean froghelper$isSelfAccessory() {
        return froghelper$selfAccessory;
    }
}
