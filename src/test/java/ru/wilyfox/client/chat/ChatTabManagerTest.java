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
        manager.updateSearch("");
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

    @Test
    void zeroExtraHistoryRetainsTheVanillaHundredMessages() {
        assertEquals(100, ChatTabManager.totalHistoryLimit(0));
        assertEquals(300, ChatTabManager.totalHistoryLimit(200));
        assertEquals(10100, ChatTabManager.totalHistoryLimit(Integer.MAX_VALUE));
        assertEquals(100, ChatTabManager.totalHistoryLimit(-1));
    }

    @Test
    void oneGlobalBoundEvictsMessagesFromEveryTabTogether() {
        manager.captureIncoming(Component.literal("G Fox: oldest"), 2);
        manager.captureIncoming(Component.literal("C Fox: clan"), 2);
        manager.captureIncoming(Component.literal("T Fox: trade"), 2);
        assertEquals(2, manager.getMessages(ChatTab.ALL).size());
        assertEquals(0, manager.getMessages(ChatTab.GLOBAL).size());
        assertEquals(1, manager.getMessages(ChatTab.CLAN).size());
        assertEquals(1, manager.getMessages(ChatTab.TRADE).size());
        org.junit.jupiter.api.Assertions.assertSame(manager.getMessages(ChatTab.ALL).getFirst(), manager.getMessages(ChatTab.CLAN).getFirst());
    }

    @Test
    void searchIgnoresUiFormattingAndPreservesNativeMessageMetadata() {
        var signature = new net.minecraft.network.chat.MessageSignature(new byte[256]);
        var source = net.minecraft.client.multiplayer.chat.GuiMessageSource.PLAYER;
        var tag = net.minecraft.client.multiplayer.chat.GuiMessageTag.system();
        var message = new net.minecraft.client.multiplayer.chat.GuiMessage(42, Component.literal("§6[12:34:56] G Fox: Красный Дракон"), signature, source, tag);
        manager.captureIncoming(message, 3, false);
        var entry = manager.getMessages(ChatTab.ALL).getFirst();
        org.junit.jupiter.api.Assertions.assertSame(message, entry.message());
        org.junit.jupiter.api.Assertions.assertSame(signature, entry.message().signature());
        assertEquals(42, entry.message().addedTime());
        assertEquals(source, entry.message().source());
        org.junit.jupiter.api.Assertions.assertSame(tag, entry.message().tag());
        manager.updateSearch("  КРАСНЫЙ дракон  ");
        assertEquals(1, manager.matchingMessages());
        manager.updateSearch("12:34:56");
        assertEquals(0, manager.matchingMessages());
        manager.updateSearch("missing");
        assertEquals(0, manager.matchingMessages());
        assertEquals(1, manager.getMessages(ChatTab.ALL).size());
        assertEquals(1, manager.unread(ChatTab.GLOBAL));
        manager.updateSearch("");
        manager.markActiveRead();
        assertEquals(0, manager.unread(ChatTab.GLOBAL));
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
