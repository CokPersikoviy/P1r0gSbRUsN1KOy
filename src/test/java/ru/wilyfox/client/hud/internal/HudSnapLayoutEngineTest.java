package ru.wilyfox.client.hud.internal;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.indicators.CornerSnapIndicator;
import ru.wilyfox.client.hud.indicators.ScreenAnchor;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.widget.Widget;
import ru.wilyfox.client.hud.widget.WidgetCorner;
import ru.wilyfox.client.hud.config.WidgetLayoutConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HudSnapLayoutEngineTest {
    @Test
    void temporaryGrowthDoesNotPermanentlyMoveAFreeRoot() {
        var parent = new TestWidget("Root", 700);
        var child = new TestWidget("Child", 725);
        child.setWidgetSnap("Root", WidgetCorner.TOP_LEFT, WidgetCorner.TOP_RIGHT);
        var saved = new WidgetLayoutConfig(); saved.x = 700; saved.y = 100;
        var host = new TestHost(null, saved);
        var engine = engine(parent, child);
        for (int cycle = 0; cycle < 20; cycle++) {
            child.resizeWidth(200);
            engine.updateSnappedWidgets(host);
            assertEquals(575, parent.getLeft()); assertEquals(800, child.getRight());
            child.resizeWidth(20);
            engine.updateSnappedWidgets(host);
            assertEquals(700, parent.getLeft()); assertEquals(725, child.getLeft());
            assertEquals(700, saved.x, "Display correction must not overwrite saved position");
        }
    }

    @Test
    void normalizingAValidTreeKeepsItsParentEvenIfAChildIsAboveOrLeft() {
        var parent = new TestWidget("Parent", 200);
        var child = new TestWidget("Child", 175);
        child.setStartY(83);
        child.setWidgetSnap("Parent", WidgetCorner.BOTTOM_RIGHT, WidgetCorner.TOP_LEFT);
        var engine = engine(parent, child);
        for (int frame = 0; frame < 10; frame++) {
            HudSnapGraphNormalizer.normalize(engine.getAbstractWidgetMap());
            engine.updateSnappedWidgets(new TestHost(null));
            assertFalse(parent.hasWidgetSnap(), "Normalization reversed a valid dependency");
            assertEquals("Parent", child.getSnapTargetKey());
            assertEquals(200, parent.getLeft()); assertEquals(100, parent.getTop());
            assertEquals(175, child.getLeft()); assertEquals(83, child.getTop());
        }
    }

    @Test
    void normalizingAStoredCycleStillRepairsIt() {
        var first = new TestWidget("First", 100);
        var second = new TestWidget("Second", 125);
        first.setWidgetSnap("Second", WidgetCorner.TOP_LEFT, WidgetCorner.TOP_RIGHT);
        second.setWidgetSnap("First", WidgetCorner.TOP_RIGHT, WidgetCorner.TOP_LEFT);
        var engine = engine(first, second);
        HudSnapGraphNormalizer.normalize(engine.getAbstractWidgetMap());
        assertFalse(first.hasWidgetSnap());
        assertEquals("First", second.getSnapTargetKey());
    }

    @Test
    void growingParentKeepsTheWholeChainInsideTheRightEdge() {
        var parent = new TestWidget("Chat", 700);
        var child = new TestWidget("Child", 725);
        var grandchild = new TestWidget("Grandchild", 750);
        child.setWidgetSnap("Chat", WidgetCorner.TOP_LEFT, WidgetCorner.TOP_RIGHT);
        grandchild.setWidgetSnap("Child", WidgetCorner.TOP_LEFT, WidgetCorner.TOP_RIGHT);
        var engine = engine(parent, child, grandchild);
        parent.resizeWidth(200);
        engine.updateSnappedWidgets(new TestHost(null));
        assertEquals(550, parent.getStartX());
        assertEquals(755, child.getStartX());
        assertEquals(780, grandchild.getStartX());
        assertEquals(800, grandchild.getRight());
        assertEquals("Chat", child.getSnapTargetKey());
        assertEquals("Child", grandchild.getSnapTargetKey());
        for (int i = 0; i < 10; i++) engine.updateSnappedWidgets(new TestHost(null));
        assertEquals(550, parent.getStartX(), "Repeated frames must not drift");
        assertEquals(780, grandchild.getStartX());
    }

    @Test
    void anchoredGroupStaysStableAtBothHorizontalAndVerticalEdges() {
        var parent = new TestWidget("Parent", 0);
        var child = new TestWidget("Child", 0);
        parent.setScreenAnchor(ScreenAnchor.BOTTOM_RIGHT);
        child.setWidgetSnap("Parent", WidgetCorner.TOP_LEFT, WidgetCorner.BOTTOM_RIGHT);
        var engine = engine(parent, child);
        var host = new TestHost(null);
        for (int frame = 0; frame < 10; frame++) {
            // Reapply the screen anchor's coordinates each frame, as the renderer does.
            parent.setStartX(772); parent.setStartY(580);
            engine.updateSnappedWidgets(host);
            assertEquals(775, parent.getRight());
            assertEquals(583, parent.getBottom());
            assertEquals(800, child.getRight());
            assertEquals(600, child.getBottom());
            assertEquals(ScreenAnchor.BOTTOM_RIGHT, parent.getScreenAnchor());
        }
        child.setWidgetSnap("Parent", WidgetCorner.BOTTOM_RIGHT, WidgetCorner.TOP_LEFT);
        parent.setScreenAnchor(ScreenAnchor.TOP_LEFT);
        parent.setStartX(8); parent.setStartY(8);
        engine.updateSnappedWidgets(host);
        assertEquals(0, child.getLeft()); assertEquals(0, child.getTop());
        assertEquals(25, parent.getLeft()); assertEquals(17, parent.getTop());
    }

    @Test
    void oversizedGroupRemainsReachableAndRestoresItsSnapsWhenSpaceReturns() {
        var parent = new TestWidget("Parent", 700);
        var child = new TestWidget("Child", 725);
        parent.resizeWidth(790);
        child.setWidgetSnap("Parent", WidgetCorner.TOP_LEFT, WidgetCorner.TOP_RIGHT);
        var engine = engine(parent, child);
        engine.updateSnappedWidgets(new TestHost(null));
        assertTrue(parent.getLeft() >= 0 && parent.getRight() <= 800);
        assertTrue(child.getLeft() >= 0 && child.getRight() <= 800);
        assertEquals("Parent", child.getSnapTargetKey());
        engine.updateSnappedWidgets(new TestHost(null), 1000, 600);
        assertEquals(parent.getRight() + 5, child.getLeft());
        assertTrue(child.getRight() <= 1000);
    }

    @Test
    void correctingPassiveLayoutDoesNotFightTheDraggedGroup() {
        var parent = new TestWidget("Parent", 790);
        var child = new TestWidget("Child", 0);
        child.setWidgetSnap("Parent", WidgetCorner.TOP_LEFT, WidgetCorner.TOP_RIGHT);
        engine(parent, child).updateSnappedWidgets(new TestHost(parent));
        assertEquals(790, parent.getLeft());
        assertEquals(815, child.getLeft());
    }

    @Test
    void previouslySavedCycleIsBrokenAndDoesNotDriftEveryFrame() {
        TestWidget first = new TestWidget("First", 100);
        TestWidget second = new TestWidget("Second", 125);
        first.setWidgetSnap("Second", WidgetCorner.TOP_LEFT, WidgetCorner.TOP_RIGHT);
        second.setWidgetSnap("First", WidgetCorner.TOP_LEFT, WidgetCorner.TOP_RIGHT);
        var engine = engine(first, second);
        var host = new TestHost(null);

        engine.updateSnappedWidgets(host);
        int firstX = first.getStartX();
        int secondX = second.getStartX();
        for (int frame = 0; frame < 6; frame++) engine.updateSnappedWidgets(host);

        assertFalse(first.hasWidgetSnap() && second.hasWidgetSnap());
        assertEquals(firstX, first.getStartX());
        assertEquals(secondX, second.getStartX());
    }

    @Test
    void rootCannotSnapBackToItsOwnChild() {
        TestWidget root = new TestWidget("Root", 100);
        TestWidget child = new TestWidget("Child", 125);
        child.setWidgetSnap("Root", WidgetCorner.TOP_LEFT, WidgetCorner.TOP_RIGHT);
        var engine = engine(root, child);

        engine.applyWidgetSnapping(new TestHost(root), 800, 600);

        assertFalse(root.hasWidgetSnap(), "Attaching root to child would create a two-widget cycle");
        assertEquals("Root", child.getSnapTargetKey());
    }

    @Test
    void unrelatedWidgetCanSnapToAnUnsnappedRoot() {
        TestWidget root = new TestWidget("Root", 100);
        TestWidget child = new TestWidget("Child", 125);
        var engine = engine(root, child);

        engine.applyWidgetSnapping(new TestHost(child), 800, 600);

        assertEquals("Root", child.getSnapTargetKey());
        assertEquals(125, child.getStartX());
        assertEquals(100, child.getStartY());
    }

    private static HudSnapLayoutEngine engine(Widget... widgets) {
        return new HudSnapLayoutEngine(List.of(widgets), 8, 12, 6, 5, 182, 22, 6, 29);
    }

    private static final class TestWidget extends AbstractWidget {
        private TestWidget(String key, int x) {
            super(x, 100, HudLayer.CONTENT);
            setConfigKey(key);
            width = 20;
            height = 12;
        }
        @Override public void render(GuiGraphicsExtractor context, DeltaTracker deltaTracker) {}
        private void resizeWidth(int value) { width = value; }
    }

    private record TestHost(Widget dragged, WidgetLayoutConfig stored) implements HudSnapLayoutHost {
        TestHost(Widget dragged) { this(dragged, null); }
        @Override public WidgetLayoutConfig getStoredPlacement(AbstractWidget widget) { return stored; }
        @Override public Widget getDraggedWidget() { return dragged; }
        @Override public void setActiveScreenAnchor(ScreenAnchor anchor) {}
        @Override public void setActiveDraggedCornerIndicator(CornerSnapIndicator indicator) {}
        @Override public void setActiveTargetCornerIndicator(CornerSnapIndicator indicator) {}
        @Override public int getLastScreenWidth() { return 800; }
        @Override public int getLastScreenHeight() { return 600; }
        @Override public void setLastScreenWidth(int width) {}
        @Override public void setLastScreenHeight(int height) {}
        @Override public boolean isScreenAnchorOccupied(ScreenAnchor anchor, Widget ignoredWidget) { return false; }
        @Override public boolean isCenterSideAnchor(ScreenAnchor anchor) { return false; }
    }
}
