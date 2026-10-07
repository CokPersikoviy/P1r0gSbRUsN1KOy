package ru.wilyfox.client.profiler;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

/** Bounded receipt timestamps, matched by packet identity rather than record equality. */
final class TransitionPacketTracker {
    private static final int LIMIT = 32;
    private final Map<Object, Long> received = new IdentityHashMap<>();
    private final ArrayDeque<Object> order = new ArrayDeque<>();

    synchronized void received(Object packet, long nanos) {
        if (received.containsKey(packet)) return;
        if (received.size() >= LIMIT) received.remove(order.removeFirst());
        received.put(packet, nanos);
        order.addLast(packet);
    }

    synchronized long begin(Object packet, long nanos) {
        Long arrival = received.remove(packet);
        if (arrival == null) return -1;
        order.removeIf(candidate -> candidate == packet);
        return Math.max(0, nanos - arrival);
    }

    synchronized void clear() {
        received.clear();
        order.clear();
    }
}
