package ru.wilyfox.client.profiler;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TransitionPacketTrackerTest {
    @Test void queueDelayIsSeparateFromHandlingAndConsumedOnce() {
        var tracker = new TransitionPacketTracker();
        Object packet = new Object();
        tracker.received(packet, 10);
        tracker.received(packet, 20);
        assertEquals(45, tracker.begin(packet, 55));
        assertEquals(-1, tracker.begin(packet, 99));
    }

    @Test void equalPacketsAreMatchedByIdentity() {
        var tracker = new TransitionPacketTracker();
        Object first = new String("packet");
        Object second = new String("packet");
        tracker.received(first, 10);
        tracker.received(second, 20);
        assertEquals(80, tracker.begin(second, 100));
        assertEquals(90, tracker.begin(first, 100));
    }

    @Test void droppedPacketsAreBoundedAndDisconnectClearsThem() {
        var tracker = new TransitionPacketTracker();
        Object oldest = new Object();
        tracker.received(oldest, 1);
        Object latest = null;
        for (int i = 0; i < 32; i++) {
            latest = new Object();
            tracker.received(latest, 2);
        }
        assertEquals(-1, tracker.begin(oldest, 5));
        assertEquals(3, tracker.begin(latest, 5));
        Object pending = new Object();
        tracker.received(pending, 5);
        tracker.clear();
        assertEquals(-1, tracker.begin(pending, 6));
    }
}
