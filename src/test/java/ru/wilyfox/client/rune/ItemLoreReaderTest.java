package ru.wilyfox.client.rune;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemLoreReaderTest {
    @BeforeAll
    static void initializeMinecraft() {
        ru.wilyfox.MinecraftTestBootstrap.initialize();
    }

    @Test
    void readsServerLoreWithoutAClientPlayerOrGeneratedTooltipLines() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.DAMAGE, 12);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Not a lore line"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("  \u00a7aStrength: +10%  "),
                Component.empty(),
                Component.literal("Pet experience: 45")
        )));

        assertEquals(List.of("Strength: +10%", "Pet experience: 45"), ItemLoreReader.read(stack));
    }

    @Test
    void anOrdinaryDamagedItemHasNoServerLore() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.DAMAGE, 12);
        assertEquals(List.of(), ItemLoreReader.read(stack));
    }
}
