package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import ru.wilyfox.bridge.ChatComponentAccessor;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.ConfigManager;

public final class ChatRegexClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        var store = ChatTabManager.getInstance();
        String[] key = {null};
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                store.clearAll(); store.setSearch(""); client.gui.setScreen(null);
                for (var tab : ConfigManager.get().chatWidgets.values()) { tab.textFilter = ""; tab.skipOthers = false; tab.detached = false; tab.deleted = false; }
                ConfigManager.get().render.collapseChatRepeats = false;
                ConfigManager.get().render.chatTimestamps = true;
                ConfigManager.layoutChanged();
                var chat = client.gui.hud.getChat(); chat.clearMessages(true);
                key[0] = ChatDock.create();
                var config = ChatDock.settings(key[0]);
                check(config.regexFilter, "New tabs must default to regex");
                config.textFilter = "(?iu)^(?!.*:).*(заш[её]л|выш[её]л).*";
                config.skipOthers = true;
                ConfigManager.layoutChanged(); ChatDock.select(key[0]);
                ChatDock.select(ChatDock.key(ChatTab.ALL));
                chat.addServerSystemMessage(Component.literal("Fox ЗАШЁЛ на сервер").withStyle(net.minecraft.ChatFormatting.GREEN));
                chat.addServerSystemMessage(Component.literal("Fox вышел с сервера"));
                chat.addServerSystemMessage(Component.literal("Fox: я зашел в шахту"));
                chat.addServerSystemMessage(Component.literal("Награда получена"));
                check(store.unread(key[0]) == 2, "Regex unread count differs from accepted messages");
                check(store.matchingMessages() == 2, "Skip Others did not exclude regex matches from ALL");
                ChatDock.select(key[0]);
                client.gui.setScreen(new ChatScreen("", false));
                var nativeChat = (ChatComponentAccessor) chat;
                check(store.matchingMessages() == 2, "Native regex tab ignores alternation or case");
                check(nativeChat.froghelper$getTrimmedMessages().size() == 2, "Native lines include rejected messages");
                store.markActiveRead(); check(store.unread(key[0]) == 0, "Regex messages could not be marked read");
                ChatDock.detach(key[0], 40, 40);
                var widget = ChatDock.widget(key[0]);
                check(widget.view().matchingMessages() == 2, "Detaching changed the regex result");
                check(widget.view().lines(client.font, 300, 10).size() == 2, "Detached regex viewport differs");
                check(widget.view().latestActivityNanos() > 0, "Regex matches do not activate the widget");
                config.textFilter = ".*\\S+.*";
                ConfigManager.layoutChanged(); ChatDock.select(key[0]);
                check(widget.view().matchingMessages() == 4 && store.matchingMessages() == 4, "Uppercase regex escape was corrupted");
                config.textFilter = ".*Fox.*"; ConfigManager.layoutChanged(); ChatDock.select(key[0]);
                check(store.matchingMessages() == 3, "Case-sensitive regex lost original case");
                store.setSearch("ЗАШЁЛ"); check(store.matchingMessages() == 1, "Literal search lost case-insensitive behavior");
                store.setSearch("Fox|Награда"); check(store.matchingMessages() == 0, "Search unexpectedly became regex");
                store.setSearch("");
                config.textFilter = "["; ConfigManager.layoutChanged(); ChatDock.select(key[0]);
                check(widget.view().matchingMessages() == 0 && store.matchingMessages() == 0, "Invalid regex accepted messages");
                check(widget.view().latestActivityNanos() == 0, "Invalid regex activated widget visibility");
                ChatDock.select(ChatDock.key(ChatTab.ALL)); check(store.matchingMessages() == 4, "Invalid Skip Others regex blocked ALL");
                config.regexFilter = false; config.textFilter = "FOX";
                ConfigManager.layoutChanged(); ChatDock.select(key[0]);
                check(widget.view().matchingMessages() == 3 && store.matchingMessages() == 3, "Literal compatibility mode failed");
                var gson = ru.wilyfox.client.hud.config.HudConfigCodec.createGson();
                var restored = ru.wilyfox.client.hud.config.HudConfigCodec.decode(gson, gson.toJsonTree(ConfigManager.get()));
                check(!restored.chatWidgets.get(key[0]).regexFilter, "Filter mode did not persist");
            });
            context.waitTicks(2);
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); client.gui.hud.getChat().clearMessages(true);
                if (key[0] != null && ChatDock.canDelete(key[0])) ChatDock.delete(key[0]);
                ConfigManager.restoreSnapshot(original); store.clearAll(); store.setSearch("");
                ChatDock.select(ChatDock.key(ChatTab.ALL)); Client.getInstance().getHudRenderer().refreshLayout();
            });
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
