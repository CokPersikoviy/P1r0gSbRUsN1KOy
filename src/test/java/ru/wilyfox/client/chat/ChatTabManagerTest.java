package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatTabManagerTest {
    private final ChatTabManager manager = ChatTabManager.getInstance();

    @AfterEach
    void clearHistory() {
        manager.clearAll();
    }

    @Test
    void keepsOnlyTheNewestConfiguredNumberOfMessages() {
        addMessages(5, 3);

        assertEquals(List.of("message-2", "message-3", "message-4"), allMessageText());
    }

    @Test
    void reconnectKeepsAtMostThreeHundredMessages() {
        addMessages(350, 500);

        manager.trimForReconnect(500);

        assertEquals(300, manager.getMessages(ChatTab.ALL).size());
        assertEquals("message-50", allMessageText().getFirst());
        assertEquals("message-349", allMessageText().getLast());
    }

    @Test
    void reconnectHonorsSmallerConfiguredLimit() {
        addMessages(120, 120);

        manager.trimForReconnect(80);

        assertEquals(80, manager.getMessages(ChatTab.ALL).size());
        assertEquals("message-40", allMessageText().getFirst());
    }

    private void addMessages(int count, int limit) {
        for (int index = 0; index < count; index++) {
            manager.captureIncoming(Component.literal("message-" + index), limit);
        }
    }

    private List<String> allMessageText() {
        return manager.getMessages(ChatTab.ALL).stream()
                .map(entry -> entry.component().getString())
                .toList();
    }
}
