package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.ChatWidgetVisibility;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.internal.WidgetGuiState;
import ru.wilyfox.client.hud.internal.WidgetRenderScope;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class ChatWidgetVisibilityClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        var renderer = Client.getInstance().getHudRenderer();
        String key = ChatDock.key(ChatTab.CLAN);
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                renderer.setSettings(false); renderer.setEditing(false);
                ConfigManager.get().locationLayouts.clear(); ConfigManager.get().widgetLocations.clear();
                ConfigManager.get().mainLayout.widgets.clear(); ConfigManager.get().mainLayout.widgets.add(key);
                var settings = ChatDock.settings(key);
                settings.deleted = false; settings.detached = true;
                settings.channel = ChatTab.CLAN; settings.textFilter = "dragon";
                settings.visibility = ChatWidgetVisibility.CHAT_OPEN;
                ConfigManager.layoutChanged(); renderer.refreshLayout(); ChatDock.select(ChatDock.key(ChatTab.ALL));
                client.gui.hud.getChat().clearMessages(false);
                client.gui.setScreen(null);
                var widget = ChatDock.widget(key);
                check(!widget.isVisible(), "Empty transient window must be hidden");
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("T Fox: dragon"));
                check(!widget.isVisible(), "Another channel woke the window");
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("C Fox: unrelated"));
                check(!widget.isVisible(), "A filtered-out message woke the window");
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("C Fox: dragon"));
                check(widget.isVisible() && widget.visibilityAlpha() == 1, "Matching live message did not wake the window");
                age(widget.view(), 9_000_000_000L);
                var state = new GuiRenderState();
                var captured = new ArrayList<Object>();
                try (var outer = new WidgetRenderScope(captured, .8f)) {
                    widget.render(new GuiGraphicsExtractor(client, state, 0, 0), DeltaTracker.ZERO);
                    check(WidgetRenderScope.alpha() == .8f, "Chat fade leaked into the layout scope");
                }
                check(!captured.isEmpty() && WidgetRenderScope.alpha() == 1, "Chat fade lost animation capture or leaked opacity");
                int[] textCount = {0};
                state.forEachText(text -> {
                    textCount[0]++;
                    float alpha = ((WidgetGuiState.Opacity) state).froghelper$opacity(text);
                    check(alpha > .35f && alpha < .42f, "Content fade did not compose with layout opacity");
                });
                check(textCount[0] > 0, "Visible window did not render text");
                age(widget.view(), 11_000_000_000L);
                check(!widget.isVisible(), "Window did not disappear after inactivity");
                renderer.setEditing(true);
                check(widget.isVisible(), "Hidden window cannot be positioned in the editor");
                renderer.setEditing(false);
                settings.visibility = ChatWidgetVisibility.ALWAYS;
                check(widget.isVisible() && widget.visibilityAlpha() == 1, "Always mode faded");
                settings.visibility = ChatWidgetVisibility.CHAT_OPEN;
            });
            context.setScreen(() -> new ChatScreen("", false));
            context.runOnClient(client -> check(ChatDock.widget(key).isVisible()
                    && ChatDock.widget(key).visibilityAlpha() == 1, "Opening main chat did not reveal old history"));
            context.waitTicks(2); context.takeScreenshot("chat-widget-open-visibility");
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                var widget = ChatDock.widget(key);
                check(!widget.isVisible(), "Closing chat revived expired messages");
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("C Fox: another dragon"));
                check(widget.isVisible(), "Hidden window did not wake on the next message");
                // A custom FH window uses the same policy, but downloaded history is not live activity.
                widget.settings().channel = ChatTab.FH;
                var store = ChatTabManager.getInstance(); store.clearBackendMessages();
                long now = System.currentTimeMillis();
                var history = new BackendChatMessage(1, "Fox", "dragon history", now);
                store.acceptBackendMessages(List.of(history), false);
                check(!widget.isVisible(), "Downloading FH history woke a window");
                var live = new BackendChatMessage(2, "Fox", "dragon live", now);
                store.acceptBackendMessages(List.of(live), true);
                check(widget.isVisible(), "Live FH message did not wake a window");
                store.acceptBackendMessages(List.of(new BackendChatMessage(3, "Fox", "dragon history", now)), false);
                check(widget.isVisible(), "Backfill replaced the recent live-message activity");
                age(widget.view(), 11_000_000_000L);
                store.acceptBackendMessages(List.of(live), true);
                check(!widget.isVisible(), "Duplicate FH message revived expired activity");
            });
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); renderer.setEditing(false); renderer.setSettings(false);
                ConfigManager.restoreSnapshot(original); renderer.refreshLayout();
                client.gui.hud.getChat().clearMessages(false); ChatTabManager.getInstance().clearBackendMessages();
                ChatDock.select(ChatDock.key(ChatTab.ALL));
            });
        }
    }
    private static void age(ChatWidgetView view, long age) {
        view.latestActivityNanos();
        try {
            Field field = ChatWidgetView.class.getDeclaredField("latestActivity"); field.setAccessible(true);
            field.setLong(view, System.nanoTime() - age);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
