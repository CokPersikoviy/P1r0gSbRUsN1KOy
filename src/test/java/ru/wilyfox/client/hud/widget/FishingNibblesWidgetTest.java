package ru.wilyfox.client.hud.widget;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FishingNibblesWidgetTest {
    @Test
    void dimensionSortingUsesFishingSpotOrderInsteadOfNibbleValue() {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(List.of(
                Map.entry("crystal", 500.0D),
                Map.entry("magma", 400.0D),
                Map.entry("swamp", 300.0D),
                Map.entry("bay", 10.0D),
                Map.entry("ambergrot", 200.0D),
                Map.entry("endwharf", 600.0D)
        ));

        entries.sort(FishingNibblesWidget.dimensionComparator());

        assertEquals(
                List.of("swamp", "bay", "magma", "ambergrot", "endwharf", "crystal"),
                entries.stream().map(Map.Entry::getKey).toList()
        );
    }
}
