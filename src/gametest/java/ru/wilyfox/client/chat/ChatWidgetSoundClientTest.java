package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import ru.wilyfox.client.audio.UiSound;
import ru.wilyfox.client.hud.config.*;
import java.util.List;

public final class ChatWidgetSoundClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        double ui = context.computeOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.UI).get());
        double master = context.computeOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get());
        int[] sounds = {0};
        SoundEventListener listener = (instance, event, distance) -> {
            if (instance.getIdentifier().toString().equals(UiSound.TOGGLE.eventId())) {
                expect(instance.getSource() == SoundSource.UI, "Chat alert bypassed UI volume"); sounds[0]++;
            }
        };
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.getSoundSourceOptionInstance(SoundSource.UI).set(1d);
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1d);
                ConfigManager.get().chatWidgets.values().forEach(window -> window.messageSound = false);
                ConfigManager.get().mainLayout.widgets.clear(); ConfigManager.get().locationLayouts.clear();
                ConfigManager.get().widgetLocations.clear(); ConfigManager.setEditorLayout(null);
                String key = ChatDock.key(ChatTab.CLAN);
                var window = ChatDock.settings(key);
                window.deleted = false; window.detached = true; window.channel = ChatTab.CLAN;
                window.regexFilter = true; window.textFilter = ".*dragon.*"; window.visibility = ChatWidgetVisibility.CHAT_OPEN;
                ConfigManager.get().mainLayout.widgets.add(key); ConfigManager.layoutChanged();
                client.getSoundManager().addListener(listener);
                var chat = client.gui.hud.getChat();
                var store = ChatTabManager.getInstance();
                chat.addServerSystemMessage(Component.literal("C Fox: dragon disabled"));
                expect(sounds[0] == 0, "Disabled sound still played");
                window.messageSound = true;
                chat.addServerSystemMessage(Component.literal("T Fox: dragon"));
                chat.addServerSystemMessage(Component.literal("C Fox: unrelated"));
                expect(sounds[0] == 0, "Wrong channel or text filter played sound");
                chat.addServerSystemMessage(Component.literal("C Fox: dragon live"));
                expect(sounds[0] == 1, "Live matching message did not play toggle sample");
                store.captureIncoming(store.getMessages(ChatTab.CLAN).getLast().message(), false);
                store.rebuildVanillaChat();
                var widget = ChatDock.widget(key);
                for (int i = 0; i < 3; i++) widget.render(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO);
                expect(sounds[0] == 1, "Duplicate capture or rendering repeated the sound");

                String custom = "ChatWidgetCustom_aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
                var second = new ChatWidgetConfig(ChatTab.CLAN);
                second.detached = true; second.messageSound = true; second.textFilter = ".*dragon.*";
                ConfigManager.get().chatWidgets.put(custom, second); ConfigManager.get().mainLayout.widgets.add(custom);
                ConfigManager.get().chatTabOrder.add(custom);
                ConfigManager.layoutChanged();
                chat.addServerSystemMessage(Component.literal("C Fox: dragon two windows"));
                expect(sounds[0] == 2, "Several matching windows overlapped their alerts");
                ConfigManager.get().mainLayout.widgets.remove(custom); ConfigManager.layoutChanged();
                ConfigManager.getWidgetLocations(key).locationVisibility.add("#missing-location"); ConfigManager.layoutChanged();
                chat.addServerSystemMessage(Component.literal("C Fox: dragon inactive layout"));
                expect(sounds[0] == 2, "Inactive window still alerted");
                ConfigManager.getWidgetLocations(key).locationVisibility.clear(); ConfigManager.layoutChanged();
                window.detached = false;
                chat.addServerSystemMessage(Component.literal("C Fox: dragon docked"));
                expect(sounds[0] == 2, "Docked tab still alerted as a widget");

                second.channel = ChatTab.FH; second.textFilter = ".*ping.*";
                ConfigManager.get().mainLayout.widgets.add(custom); ConfigManager.layoutChanged();
                long id = System.currentTimeMillis();
                store.acceptBackendMessages(List.of(new BackendChatMessage(id, "Fox", "ping history", id)), false);
                expect(sounds[0] == 2, "Loading backend history played an alert");
                var live = new BackendChatMessage(id + 1, "Fox", "ping live", id);
                store.acceptBackendMessages(List.of(live), true);
                expect(sounds[0] == 3, "Live backend message did not play an alert");
                store.acceptBackendMessages(List.of(live), true);
                store.acceptBackendMessages(List.of(new BackendChatMessage(id + 2, "Fox", "filtered out", id)), true);
                expect(sounds[0] == 3, "Repeated backend delivery or filtered message played an alert");
                ConfigManager.save();
                ConfigManager.restoreSnapshot(ConfigManager.snapshot());
                expect(ConfigManager.get().chatWidgets.get(custom).messageSound, "Sound preference did not survive config reload");
                System.out.println("Chat widget audio: toggle sample, filters, UI volume, duplicates, history and config reload passed");
            });
        } finally {
            context.runOnClient(client -> {
                client.getSoundManager().removeListener(listener);
                client.options.getSoundSourceOptionInstance(SoundSource.UI).set(ui);
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(master);
                ConfigManager.restoreSnapshot(original);
            });
        }
    }
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(value + ": " + message); }
}
