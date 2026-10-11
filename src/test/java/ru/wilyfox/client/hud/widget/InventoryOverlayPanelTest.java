package ru.wilyfox.client.hud.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryOverlayPanelTest {
    @Test void runePlateKeepsItsRightEdgeWhenScaled() {
        var placement = InventoryOverlayPanel.place(300, 80, 120, 50, 150, true, 800, 600);
        assertEquals(1.5f, placement.scale());
        assertEquals(300f, placement.x() + 120 * placement.scale());
        assertEquals(80f, placement.y());
    }

    @Test void inventoryPlateStaysOnScreenAtTheRightAndBottomEdges() {
        var placement = InventoryOverlayPanel.place(750, 590, 120, 50, 200, false, 800, 600);
        assertEquals(2f, placement.scale());
        assertEquals(798f, placement.x() + 120 * placement.scale());
        assertEquals(598f, placement.y() + 50 * placement.scale());
    }

    @Test void OversizedPlateFitsSmallViewport() {
        var placement = InventoryOverlayPanel.place(-30, 10, 200, 80, 300, true, 180, 100);
        assertTrue(placement.scale() < 3f);
        assertTrue(placement.x() >= 2f && placement.y() >= 2f);
        assertTrue(placement.x() + 200 * placement.scale() <= 178.001f);
        assertTrue(placement.y() + 80 * placement.scale() <= 98.001f);
    }
}
