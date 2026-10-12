package ru.wilyfox.client.hud.snap;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.config.HudSnapConfig;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static ru.wilyfox.client.hud.snap.SnapSession.*;

class SnapSessionTest {
    private static HudSnapConfig config() { var c = new HudSnapConfig(); c.screenTargets = false; return c; }
    private static Target target(String id, double x, double y, double w, double h) { return new Target(id, new Rect(x, y, w, h)); }
    private static SnapSession session(Rect moving, Target... targets) { return new SnapSession(moving, moving, List.of(targets), Set.of()); }
    private static Result solve(SnapSession s, double x, double y, HudSnapConfig c) { return s.solve(x, y, 1000, 800, 1, c, false); }

    @Test void dockingAtTallNeighborNeverTeleportsToItsFarCorner() {
        var s = session(new Rect(208, 220, 40, 40), target("tall", 100, 100, 100, 300));
        var result = solve(s, 208, 220, config());
        assertEquals(205, result.x()); assertEquals(220, result.y());
        assertEquals(Kind.DOCK, result.horizontal().kind()); assertNull(result.vertical());
    }
    @Test void eachAxisUsesItsOwnNeighbor() {
        var s = session(new Rect(208, 220, 40, 40), target("tall", 100, 100, 100, 300), target("below", 208, 265, 40, 40));
        var result = solve(s, 208, 220, config());
        // The closest X guide is the second widget's edge, independent of the Y dock.
        assertEquals(208, result.x()); assertEquals(220, result.y());
        assertEquals("below", result.horizontal().targetId()); assertEquals(Kind.DOCK, result.vertical().kind());
        var c = config(); c.edgeAlignment = c.centerAlignment = false;
        result = solve(s, 208, 220, c);
        assertEquals(205, result.x()); assertEquals(220, result.y());
        assertEquals("tall", result.horizontal().targetId()); assertEquals("below", result.vertical().targetId());
    }
    @Test void anOverlapDoesNotTriggerAForcedJumpOutsideTheNeighbor() {
        var result = solve(session(new Rect(100, 100, 40, 40), target("other", 100, 100, 80, 80)), 100, 100, config());
        assertEquals(100, result.x()); assertEquals(100, result.y());
    }
    @Test void retainedGuideWinsUntilReleaseAndControlClearsRetention() {
        var s = session(new Rect(100, 100, 20, 20), target("a", 100, 300, 20, 20), target("b", 104, 300, 20, 20));
        assertEquals(100, solve(s, 100, 100, config()).x());
        assertEquals(100, solve(s, 106, 100, config()).x());
        assertEquals(113, solve(s, 113, 100, config()).x());
        assertNull(s.result().horizontal());
        assertEquals(104, solve(s, 106, 100, config()).x());
        assertEquals(106, s.solve(106, 100, 1000, 800, 1, config(), true).x());
        assertNull(s.result().horizontal());
    }
    @Test void captureDistanceIsMeasuredInPhysicalPixelsAtEveryGuiScale() {
        for (int scale : new int[]{1, 2, 4}) {
            var s = session(new Rect(100, 100, 20, 20), target("a", 100, 300, 20, 20));
            assertEquals(100, s.solve(100 + 7.0 / scale, 100, 1000, 800, scale, config(), false).x());
            var fresh = session(new Rect(100, 100, 20, 20), target("a", 100, 300, 20, 20));
            assertNull(fresh.solve(100 + 9.0 / scale, 100, 1000, 800, scale, config(), false).horizontal());
        }
    }
    @Test void movingChildrenAreNeverCandidatesEvenWhenTheirRelationsWereTemporarilyCleared() {
        var root = new Rect(100, 100, 40, 40);
        var s = new SnapSession(root, new Rect(100, 100, 85, 40), List.of(target("root", 100, 100, 40, 40), target("child", 145, 100, 40, 40)), Set.of("root", "child"));
        assertTrue(s.targets().isEmpty());
        assertNull(solve(s, 105, 100, config()).horizontal());
    }
    @Test void snappingAndClampingUseTheWholeSelection() {
        var root = new Rect(100, 100, 40, 40);
        var s = new SnapSession(root, new Rect(100, 100, 85, 40), List.of(target("target", 190, 100, 40, 40)), Set.of("root", "child"));
        var result = solve(s, 102, 100, config());
        assertEquals(100, result.x()); assertEquals(185, result.bounds().right());
        result = s.solve(990, 790, 1000, 800, 1, config(), true);
        assertEquals(915, result.x()); assertEquals(1000, result.bounds().right()); assertEquals(800, result.bounds().bottom());
    }
    @Test void groupWithMembersLeftAndAboveTheRootClampsAsAUnit() {
        var root = new Rect(100, 100, 40, 40);
        var s = new SnapSession(root, new Rect(55, 50, 85, 90), List.of(), Set.of());
        var result = solve(s, 0, 0, config());
        assertEquals(45, result.x()); assertEquals(50, result.y());
        assertEquals(0, result.bounds().x()); assertEquals(0, result.bounds().y());
    }
    @Test void oversizedSelectionKeepsItsRootReachable() {
        var root = new Rect(100, 100, 40, 40);
        var s = new SnapSession(root, new Rect(100, 100, 1200, 40), List.of(), Set.of());
        assertEquals(960, solve(s, 2000, 100, config()).x());
        assertEquals(0, solve(s, -1000, 100, config()).x());
    }
    @Test void edgeAndCenterAlignmentCanBeSelectedIndependently() {
        var c = config(); c.edgeAlignment = false;
        var s = session(new Rect(82, 100, 60, 20), target("other", 100, 300, 20, 20));
        var result = solve(s, 82, 100, c);
        assertEquals(80, result.x()); assertEquals(Kind.CENTER, result.horizontal().kind());
        c.centerAlignment = false;
        assertNull(solve(s, 82, 100, c).horizontal());
    }
    @Test void equalSpacingMatchesExistingGapAndCentersBetweenNeighbors() {
        var a = target("a", 100, 100, 20, 20); var b = target("b", 160, 100, 20, 20);
        var result = solve(session(new Rect(222, 100, 20, 20), a, b), 222, 100, config());
        assertEquals(220, result.x()); assertEquals(Kind.GAP, result.horizontal().kind()); assertEquals(40, result.horizontal().gap());
        result = solve(session(new Rect(132, 100, 20, 20), a, b), 132, 100, config());
        assertEquals(130, result.x()); assertEquals(Kind.GAP, result.horizontal().kind());
        assertEquals(10, result.horizontal().gap(), "Guide should label the actual space on each side");
    }
    @Test void equalDistanceChoicesAreDeterministicIndependentOfInputOrder() {
        var a = target("a", 100, 300, 20, 20); var b = target("b", 104, 300, 20, 20);
        assertEquals("a", solve(session(new Rect(102, 100, 20, 20), b, a), 102, 100, config()).horizontal().targetId());
        assertEquals("a", solve(session(new Rect(102, 100, 20, 20), a, b), 102, 100, config()).horizontal().targetId());
    }
    @Test void screenAndHotbarGuidesCanBeDisabledTogether() {
        var s = session(new Rect(8, 100, 20, 20), target("@hotbar", 13, 100, 100, 20));
        assertNull(solve(s, 8, 100, config()).horizontal());
        var c = config(); c.screenTargets = true;
        assertEquals(Kind.SCREEN, solve(s, 9, 100, c).horizontal().kind());
        c.enabled = false;
        assertNull(solve(s, 9, 100, c).horizontal());
    }
}
