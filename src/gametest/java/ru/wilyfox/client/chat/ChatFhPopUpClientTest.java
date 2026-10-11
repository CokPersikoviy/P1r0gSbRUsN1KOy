package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.popup.PopUpManager;
import ru.wilyfox.client.popup.PopUpSource;
import java.util.List;

public final class ChatFhPopUpClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        var popUps = PopUpManager.getInstance();
        var chat = ChatTabManager.getInstance();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.get().popUps.fhMessageEvent = true;
                ConfigManager.get().popUps.holdMillis = 60_000;
                ConfigManager.get().chatWidgets.values().forEach(tab -> { tab.messageSound = false; tab.textFilter = ""; });
                ConfigManager.get().mainLayout.widgets.clear(); ConfigManager.get().locationLayouts.clear();
                ConfigManager.layoutChanged();
                chat.clearBackendMessages(); popUps.removeSources(PopUpSource.FH_MESSAGE);
                ChatDock.select(ChatDock.key(ChatTab.ALL));
                long now = System.currentTimeMillis();
                chat.acceptBackendMessages(List.of(new BackendChatMessage(now, "OtherPlayer", "history", now)), false);
                check(count() == 0, "FH history created a pop-up");
                var incoming = new BackendChatMessage(now + 1, "OtherPlayer", "Привет из FH!", now);
                chat.acceptBackendMessages(List.of(incoming), true);
                check(count() == 1, "New FH message requires a detached widget or active FH tab");
                var notification = popUps.getVisibleNotifications(32).stream().filter(n -> PopUpSource.FH_MESSAGE.equals(n.source())).findFirst().orElseThrow();
                check(notification.title().equals("FH: OtherPlayer") && notification.message().equals("Привет из FH!"), "Sender or message preview missing");
                chat.acceptBackendMessages(List.of(incoming), true); chat.rebuildVanillaChat();
                check(count() == 1, "Duplicate delivery or chat rebuild repeated a pop-up");
                chat.acceptBackendMessages(List.of(new BackendChatMessage(now + 2, client.player.getGameProfile().name(), "own message", now)), true);
                check(count() == 1, "Own FH message created an incoming pop-up");
                ConfigManager.get().popUps.fhMessageEvent = false;
                chat.acceptBackendMessages(List.of(new BackendChatMessage(now + 3, "OtherPlayer", "disabled", now)), true);
                check(count() == 1, "Disabled FH event still created a pop-up");
                ConfigManager.restoreSnapshot(ConfigManager.snapshot());
                check(!ConfigManager.get().popUps.fhMessageEvent, "FH event preference did not survive reload");
                ConfigManager.get().popUps.fhMessageEvent = true;
                ChatDock.select(ChatDock.key(ChatTab.FH)); client.gui.setScreen(new ChatScreen("", false));
                chat.acceptBackendMessages(List.of(new BackendChatMessage(now + 4, "OtherPlayer", "active FH", now)), true);
                check(count() == 2, "Reading FH suppressed the requested pop-up");
                chat.acceptBackendMessages(List.of(new BackendChatMessage(now + 5, "OtherPlayer", "older page", now)), false);
                check(count() == 2, "Older FH history page created a pop-up");
                System.out.println("FH pop-ups: sender, text, duplicates, history, own messages, toggle and active tab passed");
            });
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); chat.clearBackendMessages(); popUps.removeSources(PopUpSource.FH_MESSAGE);
                ChatDock.select(ChatDock.key(ChatTab.ALL)); ConfigManager.restoreSnapshot(original);
            });
        }
    }
    private static long count() {
        return PopUpManager.getInstance().getVisibleNotifications(32).stream().filter(n -> PopUpSource.FH_MESSAGE.equals(n.source())).count();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
