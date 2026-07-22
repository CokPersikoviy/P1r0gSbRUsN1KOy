package ru.wilyfox.client.recipe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PotionRecipeTrackerTest {
    @Test
    void slicesEvoPlusRecipeBoundsAndParsesActions() {
        PotionRecipeTracker.ParsedRecipe recipe = PotionRecipeTracker.parseLore(List.of(
                "Potion",
                "\u0420\u0435\u0446\u0435\u043f\u0442:",
                "Description",
                "10\u0441. - \u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \"\u041f\u044b\u043b\u044c\" x2",
                "5\u0441. - \u041d\u0430\u0436\u0430\u0442\u044c \u041a\u043d\u043e\u043f\u043a\u0430",
                "Boundary",
                "Service line",
                "\u0412\u0430\u0448 \u0443\u0440\u043e\u0432\u0435\u043d\u044c \u043c\u0430\u0441\u0442\u0435\u0440\u0441\u0442\u0432\u0430: 100"
        ));

        assertEquals(List.of(
                "Description",
                "10\u0441. - \u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \"\u041f\u044b\u043b\u044c\" x2",
                "5\u0441. - \u041d\u0430\u0436\u0430\u0442\u044c \u041a\u043d\u043e\u043f\u043a\u0430",
                "Boundary"
        ), recipe.displayLines());
        assertEquals(List.of(
                new PotionRecipeTracker.RecipeAction(10, "\u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u041f\u044b\u043b\u044c x2"),
                new PotionRecipeTracker.RecipeAction(5, "\u041d\u0430\u0436\u0430\u0442\u044c \u041a\u043d\u043e\u043f\u043a\u0430 x1")
        ), recipe.actions());
    }

    @Test
    void ignoresLoreWithoutBothRecipeBoundaries() {
        assertNull(PotionRecipeTracker.parseLore(List.of(
                "Potion",
                "\u0420\u0435\u0446\u0435\u043f\u0442:",
                "10\u0441. - \u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u041f\u044b\u043b\u044c"
        )));
    }
}
