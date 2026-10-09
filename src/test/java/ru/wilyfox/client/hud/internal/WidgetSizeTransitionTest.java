package ru.wilyfox.client.hud.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WidgetSizeTransitionTest {
    private static final long START = 1_000_000_000L;
    @Test void growsFromZeroToFullSizeInTwoHundredMilliseconds() {
        var animation = new WidgetSizeTransition(false);
        animation.target(true, START);
        assertEquals(0, animation.size(START));
        assertEquals(.5f, animation.size(START + 100_000_000L));
        assertEquals(1, animation.size(START + 200_000_000L));
        assertEquals(1, animation.size(START + 1_000_000_000L));
        assertTrue(animation.present());
    }
    @Test void shrinksFromFullSizeToZeroInTwoHundredMilliseconds() {
        var animation = new WidgetSizeTransition(true);
        animation.target(false, START);
        assertEquals(1, animation.size(START));
        assertEquals(.5f, animation.size(START + 100_000_000L));
        assertEquals(0, animation.size(START + 200_000_000L));
        assertEquals(0, animation.size(START + 1_000_000_000L));
        assertFalse(animation.present());
    }
    @Test void reversalsContinueFromTheCurrentSizeWithoutRestartingRepeatedTargets() {
        var animation = new WidgetSizeTransition(false);
        animation.target(true, START);
        animation.target(true, START + 50_000_000L);
        assertEquals(.5f, animation.size(START + 100_000_000L));
        animation.target(false, START + 100_000_000L);
        assertEquals(.5f, animation.size(START + 100_000_000L));
        assertEquals(.25f, animation.size(START + 200_000_000L));
        animation.target(true, START + 200_000_000L);
        assertEquals(.25f, animation.size(START + 200_000_000L));
        assertEquals(.625f, animation.size(START + 300_000_000L));
        assertEquals(1, animation.size(START + 400_000_000L));
    }
}
