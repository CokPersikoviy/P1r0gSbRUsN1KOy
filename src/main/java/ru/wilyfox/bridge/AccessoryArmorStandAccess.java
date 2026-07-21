package ru.wilyfox.bridge;

/**
 * Implemented (via mixin) by {@code ArmorStand} to flag DiamondWorld cosmetic stands whose accessory
 * owner is the local player, so the renderer can hide them in first person.
 */
public interface AccessoryArmorStandAccess {
    boolean froghelper$isSelfAccessory();
}
