package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import ru.wilyfox.bridge.ChatComponentAccessor;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetCatalog;
import java.util.List;

public final class ChatUnreadClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        String[] custom = new String[2];
        var store = ChatTabManager.getInstance();
        String all = ChatDock.key(ChatTab.ALL), fh = ChatDock.key(ChatTab.FH), global = ChatDock.key(ChatTab.GLOBAL);
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var config = ConfigManager.get();
                config.render.extraChatHistoryLines = 0;
                config.render.chatTimestamps = false;
                for (var catalog : WidgetCatalog.values()) if (catalog.chatChannel() != null) {
                    var tab = config.chatWidgets.get(catalog.key());
                    tab.channel = catalog.chatChannel(); tab.deleted = false; tab.detached = false;
                    tab.textFilter = ""; tab.skipOthers = false;
                }
                custom[0] = ChatDock.create(); custom[1] = ChatDock.create();
                var fox = ChatDock.settings(custom[0]); fox.title = "Fox"; fox.channel = ChatTab.GLOBAL; fox.textFilter = "(?i).*fox.*";
                var wolf = ChatDock.settings(custom[1]); wolf.title = "Wolf"; wolf.channel = ChatTab.GLOBAL; wolf.textFilter = "(?i).*wolf.*";
                ConfigManager.layoutChanged();
                client.gui.hud.getChat().clearMessages(false); ChatDock.select(fh);
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("G Wolf: one"));
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("G Wolf: two"));
                check(store.unread(global) == 2 && store.unread(custom[1]) == 2 && store.unread(custom[0]) == 0,
                        "Empty filtered tab inherited the channel's badge");
                ChatDock.select(custom[0]);
                check(store.matchingMessages() == 0 && store.unread(custom[0]) == 0
                        && ((ChatComponentAccessor) client.gui.hud.getChat()).froghelper$getTrimmedMessages().isEmpty(),
                        "Fox must be empty with no unread badge");
                check(store.unread(custom[1]) == 2, "Opening an empty tab read another tab's messages");
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("G Fox: match"));
                check(store.unread(custom[0]) == 1 && store.unread(custom[1]) == 2, "Custom badge ignored its own filter");
            });
            context.setScreen(() -> new ChatScreen("", false));
            context.runOnClient(client -> {
                check(store.unread(custom[0]) == 0 && store.unread(custom[1]) == 2, "Opening chat did not read just the filtered tab");
                store.setSearch("absent");
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("G Fox: hidden by search"));
                check(store.unread(custom[0]) == 1, "Search-hidden message was marked read");
                store.setSearch(""); check(store.unread(custom[0]) == 0, "Clearing search did not read revealed messages");
                for (int i = 0; i < 35; i++) client.gui.hud.getChat().addServerSystemMessage(Component.literal("G Fox: scroll history " + i));
                client.gui.hud.getChat().scrollChat(3);
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("G Fox: while scrolled"));
                store.markActiveRead();
                check(store.unread(custom[0]) == 1, "New message below a scrolled viewport was marked read");
                ChatDock.select(fh); ChatDock.select(custom[0]);
                check(store.unread(custom[0]) == 1, "Restoring a scrolled tab read messages below its viewport");
                client.gui.hud.getChat().scrollChat(-1000);
                check(store.unread(custom[0]) == 0, "Returning to latest messages did not clear the badge");
                ChatDock.select(all);
                check(store.unread(global) == 0 && store.unread(custom[1]) == 0, "ALL failed to read messages it displays");
            });
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                var chat = client.gui.hud.getChat(); chat.clearMessages(false); ChatDock.select(fh);
                String clan = ChatDock.key(ChatTab.CLAN);
                ChatDock.settings(clan).regexFilter = true; ChatDock.settings(clan).textFilter = ".*exclusive.*"; ChatDock.settings(clan).skipOthers = true;
                ConfigManager.layoutChanged();
                chat.addServerSystemMessage(Component.literal("C Fox: exclusive"));
                chat.addServerSystemMessage(Component.literal("G Wolf: visible in ALL"));
                check(store.unread(clan) == 1 && store.unread(all) == 1, "Skip Others did not filter unread badges");
                ChatDock.select(all);
                check(store.unread(clan) == 1 && store.unread(all) == 0, "ALL read an exclusive message it cannot display");
                // Refiltering must recalculate both custom badges, without transferring read status.
                ChatDock.settings(custom[0]).textFilter = ".*missing.*"; ConfigManager.layoutChanged();
                ChatDock.select(fh); chat.addServerSystemMessage(Component.literal("G Fox: new message"));
                check(store.unread(custom[0]) == 0, "Changed filter left a stale badge");
                ChatDock.settings(custom[0]).textFilter = "(?i).*fox.*"; ConfigManager.layoutChanged();
                check(store.unread(custom[0]) == 1, "Changed filter failed to count matching unread history");
                for (int i = 0; i < 105; i++) chat.addServerSystemMessage(Component.literal("T Trader: eviction " + i));
                check(store.unread(custom[0]) == 0 && store.unread(global) == 0 && store.unread(clan) == 0,
                        "Evicted messages left ghost unread badges");
                chat.clearMessages(false);
                check(store.unread(all) == 0 && store.unread(ChatTab.TRADE) == 0, "Clearing history left unread counts");
                // Backfill and duplicate FH deliveries never count as new messages.
                ChatDock.select(global); store.clearBackendMessages();
                ChatDock.settings(custom[0]).channel = ChatTab.FH; ConfigManager.layoutChanged();
                long now = System.currentTimeMillis();
                var live = new BackendChatMessage(1, "Fox", "live", now);
                store.acceptBackendMessages(List.of(live), true);
                store.acceptBackendMessages(List.of(live), true);
                store.acceptBackendMessages(List.of(new BackendChatMessage(2, "Fox", "history", now)), false);
                check(store.unread(fh) == 1 && store.unread(custom[0]) == 1, "FH duplicate/history inflated unread counts");
                ChatDock.select(custom[0]);
                check(store.unread(custom[0]) == 0 && store.unread(fh) == 1, "Reading a custom FH tab cleared the pinned FH tab");
                store.clearBackendMessages(); check(store.unread(fh) == 0, "Cleared backend history left unread counts");
                System.out.println("Unread badges: independent filters, read receipts, Skip Others, eviction, search and backend duplicates passed");
            });
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                for (String key : custom) if (key != null && ChatDock.settings(key) != null) ChatDock.delete(key);
                ConfigManager.restoreSnapshot(original); ChatDock.select(all);
                client.gui.hud.getChat().clearMessages(false); store.clearBackendMessages();
            });
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
