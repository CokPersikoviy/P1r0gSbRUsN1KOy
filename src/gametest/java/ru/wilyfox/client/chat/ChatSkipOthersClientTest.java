package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.sounds.SoundSource;
import ru.wilyfox.bridge.ChatComponentAccessor;
import ru.wilyfox.client.audio.UiSound;
import ru.wilyfox.client.hud.config.*;
import java.util.ArrayList;
import java.util.List;

public final class ChatSkipOthersClientTest implements FabricClientGameTest {
    private static final String ALL = ChatDock.key(ChatTab.ALL), FH = ChatDock.key(ChatTab.FH);
    private static final String BEFORE = ChatDock.key(ChatTab.GLOBAL), OWNER = ChatDock.key(ChatTab.CLAN), AFTER = ChatDock.key(ChatTab.TRADE);
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        double ui = context.computeOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.UI).get());
        double master = context.computeOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get());
        int[] sounds = {0};
        SoundEventListener listener = (instance, event, distance) -> {
            if (instance.getIdentifier().toString().equals(UiSound.TOGGLE.eventId())) sounds[0]++;
        };
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.getSoundSourceOptionInstance(SoundSource.UI).set(1d);
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1d);
                var c = ConfigManager.get();
                c.chatWidgets.values().forEach(tab -> { tab.skipOthers = false; tab.messageSound = false; tab.deleted = false; tab.detached = false; tab.textFilter = ""; });
                c.mainLayout.widgets.clear(); c.locationLayouts.clear(); c.widgetLocations.clear(); ConfigManager.setEditorLayout(null);
                c.chatTabOrder = new ArrayList<>(List.of(ALL, FH, BEFORE, OWNER, AFTER, ChatDock.key(ChatTab.LOCAL), ChatDock.key(ChatTab.PRIVATE)));
                for (String key : List.of(BEFORE, OWNER, AFTER)) {
                    var tab = c.chatWidgets.get(key); tab.channel = ChatTab.CLAN; tab.textFilter = "dragon";
                }
                for (String key : List.of(OWNER, AFTER)) {
                    var tab = c.chatWidgets.get(key); tab.detached = true; tab.messageSound = true; tab.visibility = ChatWidgetVisibility.CHAT_OPEN;
                    c.mainLayout.widgets.add(key);
                }
                c.chatWidgets.get(OWNER).skipOthers = true; ConfigManager.layoutChanged();
                client.getSoundManager().addListener(listener);
                var chat = client.gui.hud.getChat(); chat.clearMessages(false); ChatDock.select(ALL);
                var signature = new MessageSignature(new byte[256]);
                chat.addPlayerMessage(Component.literal("C Fox: dragon exclusive"), signature, GuiMessageTag.system());
                var store = ChatTabManager.getInstance();
                expect(store.matchingMessages() == 0 && store.unread(ChatTab.ALL) == 0, "Exclusive message leaked to ALL or its unread badge");
                expect(((ChatComponentAccessor) chat).froghelper$getTrimmedMessages().isEmpty(), "Native ALL rendered exclusive message");
                expect(ChatDock.widget(OWNER).view().matchingMessages() == 1, "Owner lost exclusive message");
                expect(ChatDock.widget(AFTER).view().matchingMessages() == 0 && ChatDock.widget(AFTER).view().selectionMessages().isEmpty(), "Later widget displayed/copied blocked message");
                expect(ChatDock.widget(AFTER).view().latestActivityNanos() == 0, "Blocked message woke later widget");
                expect(ChatDock.widget(OWNER).view().latestActivityNanos() > 0 && sounds[0] == 1, "Owner did not wake or alert exactly once");
                ChatDock.select(BEFORE); expect(store.matchingMessages() == 1, "Earlier tab lost its message"); ChatDock.select(ALL);
                c.chatWidgets.get(OWNER).messageSound = false;
                chat.addServerSystemMessage(Component.literal("C Fox: dragon silent"));
                expect(sounds[0] == 1, "Blocked later widget sounded");
                chat.addServerSystemMessage(Component.literal("C Fox: unrelated"));
                chat.addServerSystemMessage(Component.literal("T Fox: dragon different channel"));
                expect(store.matchingMessages() == 2, "Nonmatching messages lost from ALL");
                expect(store.getMessages(ChatTab.ALL).size() == 4, "Routing removed original history");
                c.chatWidgets.get(OWNER).skipOthers = false; ConfigManager.layoutChanged();
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                var store = ChatTabManager.getInstance(); var chat = client.gui.hud.getChat();
                expect(store.matchingMessages() == 4, "Turning off exclusivity did not refresh ALL: " + store.matchingMessages());
                expect(((ChatComponentAccessor) chat).froghelper$getTrimmedMessages().stream().anyMatch(line -> line.parent().signature() != null), "Native refresh lost signed message");
                expect(ChatDock.widget(AFTER).view().matchingMessages() == 2, "Widget cache did not refresh after toggle");
                expect(sounds[0] == 1, "Refreshing history replayed alert");
                var c = ConfigManager.get(); c.chatWidgets.get(OWNER).skipOthers = true;
                c.chatTabOrder.remove(AFTER); c.chatTabOrder.add(c.chatTabOrder.indexOf(OWNER), AFTER); ConfigManager.layoutChanged();
                expect(ChatDock.widget(AFTER).view().matchingMessages() == 2, "Reordering failed to change routing priority");
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                expect(ChatTabManager.getInstance().matchingMessages() == 2, "Reordered Skip Others tab leaked to ALL");
                ConfigManager.get().chatWidgets.get(OWNER).deleted = true; ConfigManager.layoutChanged();
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                expect(ChatTabManager.getInstance().matchingMessages() == 4, "Deleted tab still blocked ALL");
                ConfigManager.get().chatWidgets.get(OWNER).deleted = false;
                ConfigManager.restoreSnapshot(ConfigManager.snapshot());
                expect(ConfigManager.get().chatWidgets.get(OWNER).skipOthers, "Skip Others did not survive config roundtrip");
                System.out.println("Skip Others: ALL exclusion, native refresh, order, shared signed history, widget copy/activity/audio passed");
            });
        } finally {
            context.runOnClient(client -> {
                client.getSoundManager().removeListener(listener);
                client.options.getSoundSourceOptionInstance(SoundSource.UI).set(ui);
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(master);
                ConfigManager.restoreSnapshot(original); ChatDock.select(ALL);
            });
        }
    }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
