package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatDispatchQueueTest {
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
