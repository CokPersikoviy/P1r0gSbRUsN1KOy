package ru.wilyfox.client.hud.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WidgetTransitionTest {
    private static final long START = 1_000_000_000L;
    @Test void appearanceAndDepartureTakeExactlyHalfASecond() {
        var transition = new WidgetTransition(false, 0, 0, 1);
        transition.retarget(true, 100, 50, 2, START);
        assertEquals(0, transition.alpha(START));
        assertEquals(100, transition.x(START));
        assertEquals(2, transition.scale(START));
        assertEquals(.5f, transition.alpha(START + 250_000_000L));
        assertEquals(1, transition.alpha(START + 500_000_000L));
        transition.retarget(false, 100, 50, 2, START + 600_000_000L);
        assertEquals(1, transition.alpha(START + 600_000_000L));
        assertEquals(.5f, transition.alpha(START + 850_000_000L));
        assertEquals(0, transition.alpha(START + 1_100_000_000L));
    }
    @Test void survivingWidgetMovesAndScalesOverTwoHundredMilliseconds() {
        var transition = new WidgetTransition(true, 10, 20, 1);
        transition.retarget(true, 110, 220, 2, START);
        assertEquals(10, transition.x(START));
        assertEquals(60, transition.x(START + 100_000_000L));
        assertEquals(120, transition.y(START + 100_000_000L));
        assertEquals(1.5f, transition.scale(START + 100_000_000L));
        assertEquals(110, transition.x(START + 200_000_000L));
        assertEquals(220, transition.y(START + 200_000_000L));
        assertEquals(1, transition.alpha(START + 100_000_000L));
    }
    @Test void rapidTeleportsContinueFromTheCurrentPositionAndOpacity() {
        var transition = new WidgetTransition(true, 0, 0, 1);
        transition.retarget(true, 100, 100, 2, START);
        transition.retarget(true, 200, 200, 3, START + 100_000_000L);
        assertEquals(50, transition.x(START + 100_000_000L));
        assertEquals(125, transition.x(START + 200_000_000L));
        transition.retarget(false, 0, 0, 1, START + 200_000_000L);
        assertEquals(125, transition.x(START + 250_000_000L));
        assertEquals(.5f, transition.alpha(START + 450_000_000L));
        transition.retarget(true, 50, 0, 1, START + 450_000_000L);
        assertEquals(.5f, transition.alpha(START + 450_000_000L));
        assertEquals(.75f, transition.alpha(START + 700_000_000L));
        assertEquals(1, transition.alpha(START + 950_000_000L));
    }
    @Test void passiveAnchorsDoNotRestartMotionOrFade() {
        var transition = new WidgetTransition(true, 0, 0, 1);
        transition.retarget(true, 100, 100, 2, START);
        transition.destination(120, 120, 2);
        assertEquals(120, transition.x(START + 200_000_000L));
        transition.destination(150, 150, 2);
        assertEquals(150, transition.x(START + 210_000_000L));
    }
}
