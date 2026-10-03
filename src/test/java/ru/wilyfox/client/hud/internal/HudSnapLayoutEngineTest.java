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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HudSnapLayoutEngineTest {
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
    }

    private record TestHost(Widget dragged) implements HudSnapLayoutHost {
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
