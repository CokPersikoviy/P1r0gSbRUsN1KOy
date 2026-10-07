package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatDispatchQueueTest {
    @Test
    void boosterRepliesHavePriorityAndRapidActivationsAreNotDropped() {
        String ordinary = "priority-test-ordinary";
        String thanks = "priority-test-thx";
        int initialSize = ChatDispatchQueue.getDebugSnapshot().size();
        try {
            ChatDispatchQueue.enqueueCommand(ordinary, 0L);
            ChatDispatchQueue.enqueuePriorityCommand(thanks, 0L);
            ChatDispatchQueue.enqueuePriorityCommand(thanks, 0L);
            assertEquals(initialSize + 3, ChatDispatchQueue.getDebugSnapshot().size());
            assertEquals("/" + thanks, ChatDispatchQueue.getDebugSnapshot().preview());
            ChatDispatchQueue.removeQueuedCommandsContaining(thanks);
            // A later activation still queues a reply; there is no lossy activation cooldown.
            ChatDispatchQueue.enqueuePriorityCommand(thanks, 0L);
            assertEquals(initialSize + 2, ChatDispatchQueue.getDebugSnapshot().size());
        } finally {
            ChatDispatchQueue.removeQueuedCommandsContaining(ordinary);
            ChatDispatchQueue.removeQueuedCommandsContaining(thanks);
        }
    }

    @Test
    void removesOnlyQueuedSocialSyncCommands() {
        String syncToken = "{fhmu:socials-test";
        String ordinaryToken = "ordinary-socials-test";
        int initialSize = ChatDispatchQueue.getDebugSnapshot().size();

        try {
            ChatDispatchQueue.enqueueCommand("m Fox " + syncToken + "}", 3_000L);
            ChatDispatchQueue.enqueueCommand(ordinaryToken, 3_000L);

            ChatDispatchQueue.removeQueuedCommandsContaining("{fhmu:");

            assertEquals(initialSize + 1, ChatDispatchQueue.getDebugSnapshot().size());
        } finally {
            ChatDispatchQueue.removeQueuedCommandsContaining(syncToken);
            ChatDispatchQueue.removeQueuedCommandsContaining(ordinaryToken);
        }
    }
}
