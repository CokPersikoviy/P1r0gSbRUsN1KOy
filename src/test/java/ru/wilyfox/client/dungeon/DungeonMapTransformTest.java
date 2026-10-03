package ru.wilyfox.client.dungeon;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.protocol.DwDungeonPosition;

import static org.junit.jupiter.api.Assertions.*;

class DungeonMapTransformTest {
    @Test
    void rawCoordinatesStayCenteredAtAnyHeadingOrZoom() {
        var position = new DwDungeonPosition(37, 91);
        var transform = DungeonMapTransform.create(position, true, true, 275, 90f);
        assertEquals(36f * 128f / 126f, transform.anchorX(), 0.0001f);
        assertEquals(90f * 128f / 126f, transform.anchorY(), 0.0001f);
        assertEquals(64f, transform.project(position).x, 0.0001f);
        assertEquals(64f, transform.project(position).y, 0.0001f);
    }

    @Test
    void unanchoredMapProjectsMarkerThroughRotationAndZoom() {
        var position = new DwDungeonPosition(96, 64);
        var transform = DungeonMapTransform.create(position, false, true, 200, 90f);
        assertEquals(64f, transform.project(position).x, 0.0001f);
        assertEquals(64f + 32f * 128f / 126f * 2f, transform.project(position).y, 0.0001f);
        assertEquals(3.1f, DungeonMapTransform.create(position, false, false, 1000, 0f).zoom());
    }

    @Test
    void onlyExactUnavailablePairDisablesPositionAndStaticFallbackIgnoresTransforms() {
        assertFalse(DungeonMapTransform.hasPosition(null));
        assertFalse(DungeonMapTransform.hasPosition(new DwDungeonPosition(-1, -1)));
        assertTrue(DungeonMapTransform.hasPosition(new DwDungeonPosition(-1, 64)));
        assertTrue(DungeonMapTransform.hasPosition(new DwDungeonPosition(64, -1)));
        for (var position : new DwDungeonPosition[]{null, new DwDungeonPosition(-1, -1)}) {
            var transform = DungeonMapTransform.create(position, true, true, 310, 36f);
            assertEquals(new DungeonMapTransform(64f, 64f, 1f, 0f), transform);
        }
    }
}
