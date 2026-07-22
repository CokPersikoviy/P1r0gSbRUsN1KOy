package ru.wilyfox.client.alchemy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AutoBrewingCostOverlayTest {
    @Test
    void restoresEvoPlusAutoBrewingFormula() {
        assertEquals(0, AutoBrewingCostOverlay.getPotionsBrewed(40));
        assertEquals(1, AutoBrewingCostOverlay.getPotionsBrewed(50));
        assertEquals(192, AutoBrewingCostOverlay.autoBrewCost(40, 3));
        assertEquals(290, AutoBrewingCostOverlay.autoBrewCost(40, 4));
        assertEquals(413, AutoBrewingCostOverlay.autoBrewCost(40, 5));
        assertEquals(566, AutoBrewingCostOverlay.autoBrewCost(40, 6));
        assertEquals(240, AutoBrewingCostOverlay.autoBrewCost(50, 3));
    }

    @Test
    void parsesPriceOnlyAfterEvoPlusPriceMarker() {
        assertEquals(125, AutoBrewingCostOverlay.parsePrice("\u041a\u0443\u043f\u0438\u0442\u044c \u0437\u0430 125 \u043c\u043e\u043d\u0435\u0442"));
        assertNull(AutoBrewingCostOverlay.parsePrice("\u0426\u0435\u043d\u0430: 125"));
    }
}
