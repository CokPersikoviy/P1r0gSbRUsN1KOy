package ru.wilyfox.client.discord;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordPresenceUpdateQueueTest {
    @Test
    void coalescesTickUpdatesWithoutSchedulingOneTaskPerTick() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        List<String> applied = new ArrayList<>();
        DiscordPresenceUpdateQueue<String> queue = new DiscordPresenceUpdateQueue<>(
                tasks::add, request -> applied.add(request.presence()));

        queue.update("First location");
        queue.update("Current location");

        assertEquals(1, tasks.size());
        tasks.remove().run();
        assertEquals(List.of("Current location"), applied);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void disconnectInvalidatesAConnectionAttemptAlreadyInProgress() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        List<DiscordPresenceUpdateQueue.Request<String>> handled = new ArrayList<>();
        DiscordPresenceUpdateQueue<String> queue = new DiscordPresenceUpdateQueue<>(tasks::add, handled::add);
        queue.update("Old server");
        tasks.remove().run();
        DiscordPresenceUpdateQueue.Request<String> inFlight = handled.getFirst();

        queue.stop();
        assertFalse(queue.isCurrent(inFlight));
        tasks.remove().run();
        assertEquals(2, handled.size());
        assertNull(handled.getLast().presence());
    }

    @Test
    void reconnectAcceptsNewPresenceWithoutRevivingTheOldSession() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        List<DiscordPresenceUpdateQueue.Request<String>> handled = new ArrayList<>();
        DiscordPresenceUpdateQueue<String> queue = new DiscordPresenceUpdateQueue<>(tasks::add, handled::add);
        queue.update("Old server");
        tasks.remove().run();
        DiscordPresenceUpdateQueue.Request<String> previous = handled.getFirst();

        queue.stop();
        queue.update("New server");
        tasks.remove().run();

        assertFalse(queue.isCurrent(previous));
        assertTrue(queue.isCurrent(handled.getLast()));
        assertEquals("New server", handled.getLast().presence());
        assertNull(handled.get(1).presence());
        assertEquals(3, handled.size());
    }

    @Test
    void handlesStopIssuedFromInsideAnInFlightUpdate() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        List<String> applied = new ArrayList<>();
        @SuppressWarnings("unchecked")
        DiscordPresenceUpdateQueue<String>[] holder = new DiscordPresenceUpdateQueue[1];
        holder[0] = new DiscordPresenceUpdateQueue<>(tasks::add, request -> {
            if (request.presence() != null) {
                holder[0].stop();
                if (holder[0].isCurrent(request)) {
                    applied.add(request.presence());
                }
            } else {
                applied.add("Stopped");
            }
        });
        holder[0].update("Connecting");

        tasks.remove().run();

        assertEquals(List.of("Stopped"), applied);
        assertTrue(tasks.isEmpty());
    }
}
