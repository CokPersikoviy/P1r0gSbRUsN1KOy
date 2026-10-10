package ru.wilyfox.client.visuals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.config.VisualsConfig;
import static org.junit.jupiter.api.Assertions.*;

class VisualGeometryTest {
    @Test void compoundBlockFillOmitsSharedInternalFaces() {
        var a = new net.minecraft.world.phys.AABB(0, 0, 0, 0.5, 1, 1);
        var b = new net.minecraft.world.phys.AABB(0.5, 0, 0, 1, 1, 1);
        var faces = VisualBlockOutline.exteriorFaces(List.of(a, b));
        assertEquals(10, faces.size());
        assertFalse(faces.contains(new VisualBlockOutline.Face(a, net.minecraft.core.Direction.EAST)));
        assertFalse(faces.contains(new VisualBlockOutline.Face(b, net.minecraft.core.Direction.WEST)));
        assertEquals(12, VisualBlockOutline.exteriorFaces(List.of(a, b.move(1, 0, 0))).size());
    }
    @Test void allCrosshairShapesHaveDisjointOutlineLinesAndDot() {
        var geometry = new CrosshairGeometry();
        for (var style : VisualsConfig.CrosshairStyle.values()) for (int thickness : new int[]{1, 2, 3}) {
            var pixels = new CrosshairGeometry.Pixels(style, 6, 9, thickness, 3, true, true, true, true, 3, 2);
            var shape = geometry.shape(pixels); assertSame(shape, geometry.shape(pixels));
            var occupied = new HashSet<String>();
            for (var part : List.of(shape.outline(), shape.lines(), shape.dot())) for (var r : part) {
                assertTrue(r.width() > 0 && r.height() > 0);
                for (int y = r.y(); y < r.y() + r.height(); y++) for (int x = r.x(); x < r.x() + r.width(); x++)
                    assertTrue(occupied.add(x + ":" + y), "Blending would draw the same pixel twice: " + style);
            }
        }
    }
    @Test void subtractingOverlappingRectanglesPreservesTheUnionWithoutOverdraw() {
        var shape = CrosshairGeometry.region(List.of(new CrosshairGeometry.Rect(0, 0, 4, 4), new CrosshairGeometry.Rect(2, 2, 4, 4)),
                List.of(new CrosshairGeometry.Rect(2, 2, 2, 2)));
        assertEquals(24, shape.stream().mapToInt(r -> r.width() * r.height()).sum());
    }
    @Test void stylesRemainOnTheEdgeAndHaveBoundedSubdivision() {
        for (var style : VisualsConfig.LineStyle.values()) for (double length : new double[]{0.01, 0.5, 1, 100000}) {
            var pieces = new ArrayList<double[]>(); VisualLinePieces.forEach(style, length, (a, b) -> pieces.add(new double[]{a, b}));
            assertTrue(pieces.size() <= 128); assertEquals(0, pieces.getFirst()[0]); assertEquals(1, pieces.getLast()[1]);
            double end = 0;
            for (var p : pieces) { assertTrue(p[0] >= end - 1e-9 && p[0] < p[1] && p[1] <= 1); end = p[1]; }
        }
        var pieces = new ArrayList<double[]>(); VisualLinePieces.forEach(VisualsConfig.LineStyle.DASHED, Double.NaN, (a, b) -> pieces.add(new double[]{a, b}));
        assertTrue(pieces.isEmpty());
    }
}
