package ru.wilyfox.client.hud.snap;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.config.HudSnapConfig;
import java.util.List;
import java.util.Random;
import java.util.Set;
import static ru.wilyfox.client.hud.snap.SnapSession.*;
import static org.junit.jupiter.api.Assertions.*;

class DragCollisionTest {
    private static final Target NEIGHBOR = new Target("neighbor", new Rect(200, 100, 100, 200));
    private static HudSnapConfig config() { var c = new HudSnapConfig(); c.screenTargets = false; return c; }
    private static Result move(SnapSession session, double x, double y, boolean ctrl) { return session.solve(x, y, 1000, 800, 1, config(), ctrl); }
    private static SnapSession session(double x, double y) { var root = new Rect(x, y, 40, 40); return new SnapSession(root, root, List.of(NEIGHBOR), Set.of()); }

    @Test void sideCollisionStopsOnlyTheBlockedAxisAndAllowsSliding() {
        var s = session(400, 180);
        var r = move(s, 220, 180, false);
        assertEquals(305, r.x()); assertEquals(180, r.y()); assertEquals(Kind.DOCK, r.horizontal().kind());
        r = move(s, 220, 240, false);
        assertEquals(305, r.x()); assertEquals(240, r.y());
        assertNoOverlap(r.bounds(), NEIGHBOR.bounds());
    }
    @Test void allFourSidesStopAndFastPointerMotionCannotTunnelThrough() {
        assertEquals(155, move(session(100, 180), 450, 180, false).x());
        assertEquals(305, move(session(400, 180), 100, 180, false).x());
        assertEquals(55, move(session(220, 30), 220, 500, false).y());
        assertEquals(305, move(session(220, 400), 220, 30, false).y());
    }
    @Test void controlAllowsOverlapAndReleasingItPreventsDeepeningAPartialOverlap() {
        var s = session(400, 180);
        var r = move(s, 285, 180, true);
        assertEquals(285, r.x()); assertNull(r.horizontal());
        r = move(s, 270, 180, false);
        assertEquals(285, r.x(), "Ctrl release must not let a partial overlap grow");
        r = move(s, 350, 180, false);
        assertEquals(350, r.x());
        r = move(s, 250, 180, false);
        assertEquals(305, r.x(), "Leaving the intentional overlap must restore the barrier");
    }
    @Test void disabledMagnetStillPreventsOverlapButWithoutTheConnectionGap() {
        var c = config(); c.enabled = false;
        var r = session(400, 180).solve(220, 180, 1000, 800, 1, c, false);
        assertEquals(300, r.x()); assertNull(r.horizontal()); assertNoOverlap(r.bounds(), NEIGHBOR.bounds());
    }
    @Test void legacyOverlapsCanBeSeparatedWithoutAutomaticFarEdgeEjection() {
        var s = session(285, 180);
        assertEquals(285, move(s, 270, 180, false).x());
        assertEquals(320, move(s, 320, 180, false).x());
        assertEquals(305, move(s, 260, 180, false).x());
        var fullyInside = session(220, 180);
        assertEquals(220, move(fullyInside, 220, 180, false).x());
        assertEquals(350, move(fullyInside, 350, 180, false).x());
    }
    @Test void everyGroupMemberCollidesWithoutTreatingEmptySpaceAsASolidBody() {
        var root = new Rect(350, 150, 40, 40);
        var child = new Rect(440, 150, 40, 40);
        var s = new SnapSession(root, new Rect(350, 150, 130, 40), List.of(NEIGHBOR), Set.of("root", "child"), List.of(root, child));
        var r = move(s, 200, 150, false);
        assertEquals(305, r.x()); assertEquals(435, r.bounds().right());
        // A separate widget in the selection's empty interval must not become an obstacle for its bounding box.
        var between = new Target("between", new Rect(405, 150, 10, 10));
        s = new SnapSession(root, new Rect(350, 150, 130, 40), List.of(between), Set.of("root", "child"), List.of(root, child));
        var c = config(); c.enabled = false;
        r = s.solve(350, 150, 1000, 800, 1, c, false);
        assertEquals(350, r.x());
        r = s.solve(390, 150, 1000, 800, 1, c, false);
        assertEquals(365, r.x(), "Root, rather than the group's empty interval, must stop at the neighbor");
    }
    @Test void groupChildrenAndDecorativeHotbarAreExcludedFromPhysicalObstacles() {
        var root = new Rect(100, 100, 40, 40);
        var c = config(); c.enabled = false;
        var s = new SnapSession(root, root, List.of(new Target("child", new Rect(145, 100, 40, 40)), new Target("@hotbar", new Rect(200, 100, 182, 22))), Set.of("child"));
        assertEquals(240, s.solve(240, 100, 1000, 800, 1, c, false).x());
    }
    @Test void snapCorrectionCannotIntroduceAnOverlapAtANearbyCorner() {
        var root = new Rect(163, 63, 40, 40);
        var neighbor = new Target("corner", new Rect(200, 100, 100, 100));
        var s = new SnapSession(root, root, List.of(neighbor), Set.of());
        // This position starts overlapped, so move fully clear before testing new alignment.
        move(s, 140, 50, false);
        var r = move(s, 162, 62, false);
        assertNoOverlap(r.bounds(), neighbor.bounds());
    }
    @Test void mixedFastAndSlowMovesNeverCreateAnIntersection() {
        var targets = List.of(NEIGHBOR, new Target("second", new Rect(450, 230, 90, 70)), new Target("third", new Rect(70, 400, 160, 120)));
        var root = new Rect(20, 20, 40, 40);
        var s = new SnapSession(root, root, targets, Set.of());
        var random = new Random(12);
        for (int i = 0; i < 1500; i++) {
            var r = move(s, random.nextInt(960), random.nextInt(760), false);
            for (var target : targets) assertNoOverlap(r.bounds(), target.bounds());
        }
    }
    private static void assertNoOverlap(Rect a, Rect b) {
        assertFalse(a.x() < b.right() - .001 && a.right() > b.x() + .001
                && a.y() < b.bottom() - .001 && a.bottom() > b.y() + .001, a + " overlaps " + b);
    }
}
