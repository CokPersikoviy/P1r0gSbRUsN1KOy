package ru.wilyfox.client.recipe;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftRecipeTrackerTest {
    @Test
    void appliesInventoryDeltaToServerCounter() {
        var ingredient = CraftRecipeTracker.Ingredient.parse("• Душа энда 51/400", Map.of("Душа энда", 10));

        assertEquals("Душа энда", ingredient.name());
        assertEquals("• Душа энда 51/400", ingredient.display(Map.of("Душа энда", 10)));
        assertEquals("• Душа энда 71/400", ingredient.display(Map.of("Душа энда", 30)));
        assertEquals("• Душа энда 41/400", ingredient.display(Map.of()));
    }

    @Test
    void neverGoesNegativeAndKeepsUnparsedLines() {
        var ingredient = CraftRecipeTracker.Ingredient.parse("• Стержень 0/3", Map.of("Стержень", 2));
        assertEquals("• Стержень 0/3", ingredient.display(Map.of()));

        var plain = CraftRecipeTracker.Ingredient.parse("Любой меч", Map.of());
        assertEquals("Любой меч", plain.display(Map.of("Любой меч", 5)));
    }
}
