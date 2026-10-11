package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import ru.wilyfox.bridge.ChatComponentAccessor;
import ru.wilyfox.client.hud.config.ConfigManager;
import java.time.Instant;
import java.util.List;

public final class ChatRepeatsClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        var store = ChatTabManager.getInstance();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.get().render.collapseChatRepeats = true;
                ConfigManager.get().render.chatTimestamps = true;
                ConfigManager.get().render.extraChatHistoryLines = 200;
                for (var tab : ConfigManager.get().chatWidgets.values()) { tab.textFilter = ""; tab.skipOthers = false; tab.deleted = false; tab.detached = false; }
                var chat = client.gui.hud.getChat(); chat.clearMessages(true);
                ChatDock.select(ChatDock.key(ChatTab.ALL)); client.gui.setScreen(new ChatScreen("", false));
                for (int i = 0; i < 3; i++) {
                    final int second = i;
                    ChatMessageDecorator.withTimestamp(Instant.parse("2026-01-01T12:00:00Z").plusSeconds(second),
                            () -> chat.addServerSystemMessage(Component.literal("Способность активировалась!")));
                }
                var nativeChat = (ChatComponentAccessor) chat;
                check(nativeChat.froghelper$getAllMessages().size() == 3, "Collapse discarded original messages");
                check(nativeChat.froghelper$getTrimmedMessages().size() == 1, "Native chat still displays repeated lines");
                check(line(nativeChat.froghelper$getTrimmedMessages().getFirst().content()).endsWith("[x3]"), "Native repeat counter missing");
                var representative = nativeChat.froghelper$getAllMessages().getFirst();
                check(!representative.content().getString().contains("[x3]"), "Repeat badge entered raw chat content");
                check(store.matchingMessages() == 1, "Search count includes collapsed copies");
                var view = new ChatWidgetView(store, ChatTab.ALL);
                var lines = view.lines(client.font, 300, 10);
                check(lines.size() == 1 && line(lines.getFirst().text()).endsWith("[x3]"), "Chat widget differs from native chat");
                check(view.matchingMessages() == 1, "Widget message count includes collapsed copies");
                store.setSearch("активировалась"); chat.rescaleChat();
                check(nativeChat.froghelper$getTrimmedMessages().size() == 1 && store.repeatCount(representative) == 3,
                        "Search/rescale lost the repeat group");
                store.setSearch("");
                ConfigManager.get().render.collapseChatRepeats = false; store.clearLineCache(); store.rebuildVanillaChat();
                check(nativeChat.froghelper$getTrimmedMessages().size() == 3, "Disabling collapse did not restore originals");
                ConfigManager.get().render.collapseChatRepeats = true; store.clearLineCache(); store.rebuildVanillaChat();
                chat.addServerSystemMessage(Component.literal("Другое сообщение"));
                chat.addServerSystemMessage(Component.literal("Способность активировалась!"));
                check(nativeChat.froghelper$getTrimmedMessages().size() == 3, "Nonconsecutive messages were merged");
                check(store.repeatCount(nativeChat.froghelper$getAllMessages().getFirst()) == 1, "A new run inherited an old counter");
            });
            context.waitTicks(3); context.takeScreenshot("chat-repeated-messages");
            context.runOnClient(client -> {
                var chat = client.gui.hud.getChat(); chat.clearMessages(true); client.gui.setScreen(null);
                ChatDock.select(ChatDock.key(ChatTab.ALL));
                chat.addServerSystemMessage(Component.literal("G Fox: repeated")); chat.addServerSystemMessage(Component.literal("G Fox: repeated"));
                check(store.unread(ChatTab.GLOBAL) == 1, "Unread badge counts the hidden copy");
                ChatDock.select(ChatDock.key(ChatTab.GLOBAL)); client.gui.setScreen(new ChatScreen("", false)); store.markActiveRead();
                check(store.unread(ChatTab.GLOBAL) == 0, "Collapsed group could not be marked read");
                store.clearBackendMessages(); ChatDock.select(ChatDock.key(ChatTab.FH));
                long now = System.currentTimeMillis();
                store.acceptBackendMessages(List.of(new BackendChatMessage(1, "Alice", "Same FH text", now),
                        new BackendChatMessage(2, "Alice", "Same FH text", now + 1000)), true);
                var nativeChat = (ChatComponentAccessor) chat;
                check(nativeChat.froghelper$getTrimmedMessages().size() == 1 && line(nativeChat.froghelper$getTrimmedMessages().getFirst().content()).endsWith("[x2]"), "FH did not collapse repeated messages");
                store.acceptBackendMessages(List.of(new BackendChatMessage(3, "Bob", "Same FH text", now + 2000)), true);
                check(nativeChat.froghelper$getTrimmedMessages().size() == 2, "Different FH authors were merged");
                store.clearBackendMessages();
                store.acceptBackendMessages(List.of(new BackendChatMessage(4, "Alice", "Same FH text", now),
                        new BackendChatMessage(5, "Alice", "Same FH text", now + 1000, "discord", List.of())), false);
                check(nativeChat.froghelper$getTrimmedMessages().size() == 2, "Minecraft and Discord origins were merged");
            });
            context.waitTicks(3); context.takeScreenshot("chat-repeated-fh-origins");
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); client.gui.hud.getChat().clearMessages(true);
                ru.wilyfox.client.popup.PopUpManager.getInstance().removeSources(ru.wilyfox.client.popup.PopUpSource.FH_MESSAGE);
                ConfigManager.restoreSnapshot(original); store.clearAll(); store.setSearch(""); ChatDock.select(ChatDock.key(ChatTab.ALL));
            });
        }
    }
    private static String line(net.minecraft.util.FormattedCharSequence value) {
        var result = new StringBuilder(); value.accept((index, style, code) -> { result.appendCodePoint(code); return true; }); return result.toString();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
