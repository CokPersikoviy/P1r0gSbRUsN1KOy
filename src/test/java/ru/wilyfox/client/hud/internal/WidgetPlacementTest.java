package ru.wilyfox.client.hud.internal;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WidgetPlacementTest {
    @Test void emptyCanvasCentersAfterSidebar() {
        assertEquals(new WidgetPlacement.Position(450, 250), WidgetPlacement.find(1000, 600, 200, 300, 100, List.of()));
    }

    @Test void searchesAroundOccupiedCenter() {
        var position = WidgetPlacement.find(1000, 600, 200, 100, 100,
                List.of(new WidgetPlacement.Bounds(200, 150, 800, 450)));
        assertEquals(200, position.x());
        assertEquals(8, position.y());
    }

    @Test void fullCanvasSearchIsBoundedEvenWithHugeScreen() {
        assertTimeout(Duration.ofMillis(500), () -> {
            for (int i = 0; i < 100; i++) {
                assertEquals(new WidgetPlacement.Position(99850, 99950),
                        WidgetPlacement.find(200000, 200000, 0, 300, 100,
                                List.of(new WidgetPlacement.Bounds(0, 0, 200000, 200000))));
            }
        });
    }

    @Test void oversizedGraphDoesNotLeaveVisibleOrigin() {
        assertEquals(new WidgetPlacement.Position(0, 0), WidgetPlacement.find(427, 240, 200, 760, 700,
                List.of(new WidgetPlacement.Bounds(0, 0, 427, 240))));
    }
}
