package ru.wilyfox.client.pet;

import net.minecraft.world.item.ItemStack;

public record ActivePetInfo(
        String id,
        String name,
        ItemStack icon,
        int level,
        double exp,
        double energy,
        boolean resolved
) {
}
