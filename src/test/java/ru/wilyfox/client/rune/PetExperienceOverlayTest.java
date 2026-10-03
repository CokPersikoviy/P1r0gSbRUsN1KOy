package ru.wilyfox.client.rune;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.wilyfox.MinecraftTestBootstrap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PetExperienceOverlayTest {
    @BeforeAll
    static void initializeMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void readsExperienceFromRussianAndEnglishFishLore() {
        assertEquals(125L, PetExperienceOverlay.extractExperience(withLore("Опыт питомца: 125")));
        assertEquals(125L, PetExperienceOverlay.extractExperience(withLore("Pet experience: 125")));
    }

    @Test
    void readsCurrentBucketExperienceFromBothServerLanguages() {
        assertEquals(640L, PetExperienceOverlay.extractExperience(withLore("Опыта питомца в ведре: 640/1000")));
        assertEquals(640L, PetExperienceOverlay.extractExperience(withLore("Bucket Pet experience: 640/1000")));
    }

    @Test
    void unrelatedNumbersAndOverflowDoNotBecomeExperience() {
        assertEquals(0L, PetExperienceOverlay.extractExperience(withLore("Fish price: 125")));
        assertEquals(0L, PetExperienceOverlay.extractExperience(withLore("Pet experience: 999999999999999999999")));
    }

    private static ItemStack withLore(String line) {
        ItemStack stack = new ItemStack(Items.COD);
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(line))));
        return stack;
    }
}
