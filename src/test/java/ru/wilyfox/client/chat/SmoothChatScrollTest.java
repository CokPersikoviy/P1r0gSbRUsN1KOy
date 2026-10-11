package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmoothChatScrollTest {
    @Test void animationUsesTimeRatherThanFrameCount() {
        var slow = new SmoothChatScroll(); var fast = new SmoothChatScroll();
        slow.moveTo(8, 1_000_000_000L); fast.moveTo(8, 1_000_000_000L);
        for (long t = 1_000_000_000L; t < 1_070_000_000L; t += 1_000_000L) fast.value(t);
        assertEquals(7, slow.value(1_070_000_000L), 1e-9);
        assertEquals(slow.value(1_070_000_000L), fast.value(1_070_000_000L));
        assertEquals(8, slow.value(1_140_000_000L));
    }
    @Test void reversingWheelContinuesFromDisplayedPosition() {
        var scroll = new SmoothChatScroll();
        scroll.reset(10); scroll.moveTo(20, 1_000_000_000L);
        double before = scroll.value(1_050_000_000L);
        scroll.moveTo(5, 1_050_000_000L);
        assertEquals(before, scroll.value(1_050_000_000L));
        assertTrue(scroll.value(1_070_000_000L) < before);
        assertEquals(5, scroll.value(1_190_000_000L));
    }
    @Test void incomingMessageMovesBothEndsWithoutRestartingAnimation() {
        var scroll = new SmoothChatScroll();
        scroll.moveTo(6, 1_000_000_000L);
        double before = scroll.value(1_050_000_000L);
        scroll.shift(3);
        assertEquals(before + 3, scroll.value(1_050_000_000L));
        assertEquals(9, scroll.value(1_140_000_000L));
        scroll.reset(0);
        assertEquals(0, scroll.value(1_060_000_000L));
    }
}
