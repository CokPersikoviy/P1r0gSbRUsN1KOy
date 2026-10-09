package ru.wilyfox.client.hud.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NumberDragTest {
    @Test void startsRelativeToThePressedNumberWithoutJumping() {
        var drag = new NumberDrag();
        drag.begin(250);
        assertEquals(50, drag.update(250, 50, 0, 100, 1, false));
        assertEquals(52, drag.update(258, 50, 0, 100, 1, false));
        assertEquals(49, drag.update(246, 52, 0, 100, 1, false));
    }

    @Test void tinyMovesAccumulateInBothDirectionsIncludingAtTheLimits() {
        var drag = new NumberDrag();
        drag.begin(0);
        int value = 0;
        for (int x = 1; x <= 40; x++) value = drag.update(x, value, 0, 100, 1, true);
        assertEquals(1, value);
        drag.begin(0);
        value = 100;
        for (int x = -1; x >= -40; x--) value = drag.update(x, value, 0, 100, 1, true);
        assertEquals(99, value);
    }

    @Test void shiftRequiresTenTimesTheDistanceAndCanChangeDuringAGesture() {
        var drag = new NumberDrag();
        drag.begin(0);
        assertEquals(11, drag.update(4, 10, 0, 100, 1, false));
        assertEquals(11, drag.update(43, 11, 0, 100, 1, true));
        assertEquals(12, drag.update(44, 11, 0, 100, 1, true));
        assertEquals(13, drag.update(48, 12, 0, 100, 1, false));
    }

    @Test void clampsAndReversesImmediatelyAfterOvershooting() {
        var drag = new NumberDrag();
        drag.begin(0);
        assertEquals(100, drag.update(10000, 50, 0, 100, 1, false));
        assertEquals(99, drag.update(9996, 100, 0, 100, 1, false));
        assertEquals(0, drag.update(-10000, 99, 0, 100, 1, false));
        assertEquals(1, drag.update(-9996, 0, 0, 100, 1, false));
    }

    @Test void retainsExistingStepsAndAllowsANonAlignedMaximum() {
        var drag = new NumberDrag();
        drag.begin(0);
        assertEquals(300, drag.update(32, 100, 100, 310, 25, false));
        assertEquals(310, drag.update(36, 300, 100, 310, 25, false));
        assertEquals(285, drag.update(32, 310, 100, 310, 25, false));
    }

    @Test void respectsDynamicBoundsAndNegativeValuesWithoutIntegerOverflow() {
        var drag = new NumberDrag();
        drag.begin(0);
        assertEquals(-5, drag.update(-20, 0, -60, 60, 1, false));
        assertEquals(30, drag.update(0, 50, 0, 30, 5, false));
        assertEquals(Integer.MAX_VALUE, drag.update(10000, Integer.MAX_VALUE - 5,
                Integer.MIN_VALUE, Integer.MAX_VALUE, 100, false));
    }

    @Test void aNewGestureDiscardsThePreviousFractionalMovement() {
        var drag = new NumberDrag();
        drag.begin(0);
        assertEquals(5, drag.update(3, 5, 0, 10, 1, false));
        drag.begin(100);
        assertEquals(5, drag.update(101, 5, 0, 10, 1, false));
    }
}
