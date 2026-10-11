package ru.wilyfox.client.hud;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.chat.ChatDock;
import ru.wilyfox.client.chat.ChatTab;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.indicators.ScreenAnchor;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.widget.WidgetCorner;

/** Reproduce a real detached chat growing with a TPS widget magnetized to its right edge. */
public final class ChatSnapResizeClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
        int oldWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int oldHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        var renderer = Client.getInstance().getHudRenderer();
        String key = ChatDock.key(ChatTab.CLAN);
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                renderer.setSettings(false); renderer.setEditing(false);
                client.options.guiScale().set(2); client.getWindow().setWindowed(1280, 720); client.resizeGui();
                var config = ConfigManager.get();
                config.mainLayout.widgets.clear(); config.locationLayouts.clear(); config.widgetLocations.clear();
                config.mainLayout.widgets.add(key); config.mainLayout.widgets.add("EstimatedTpsWidget");
                config.widgetLayouts.clear(); config.render.nativeRenderer = true;
                config.estimatedTps.enabled = false;
                var chat = ChatDock.settings(key);
                chat.deleted = false; chat.detached = true; chat.width = 100; chat.rows = 5;
                chat.showTitle = false; chat.textFilter = "";
                ConfigManager.layoutChanged(); renderer.refreshLayout();
                client.gui.setScreen(new HudEditingScreen(renderer)); renderer.setEditing(true);
                renderer.onKeyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                var parent = ChatDock.widget(key); var child = child();
                parent.setScreenAnchor(null); parent.clearWidgetSnap(); parent.setScale(1);
                child.setScreenAnchor(null); child.setScale(1);
                parent.setStartX(client.getWindow().getGuiScaledWidth() - 270); parent.setStartY(100);
                child.setWidgetSnap(key, WidgetCorner.TOP_LEFT, WidgetCorner.TOP_RIGHT);
                ConfigManager.saveWidgetLayout(parent); ConfigManager.saveWidgetLayout(child);
                client.gui.hud.getChat().addServerSystemMessage(Component.literal("C Fox: Chat resizing test"));
            });
            context.waitTicks(3);
            context.runOnClient(client -> { ChatDock.settings(key).width = 381; ConfigManager.save(); });
            context.waitTicks(3);
            context.runOnClient(client -> {
                var parent = ChatDock.widget(key); var child = child();
                checkInside(parent, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
                checkInside(child, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
                check(child.getLeft() == parent.getRight() + 5, "Growing chat broke its magnetized gap");
                check(child.getSnapTargetKey().equals(key), "Growing chat removed the snap relation");
                // Repeat with a screen anchor, where the preferred root position is reapplied each frame.
                parent.setScreenAnchor(ScreenAnchor.TOP_RIGHT); ConfigManager.saveWidgetLayout(parent);
            });
            context.waitTicks(4);
            context.runOnClient(client -> {
                var parent = ChatDock.widget(key); var child = child();
                checkInside(child, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
                check(child.getLeft() == parent.getRight() + 5, "Screen anchor pushed the child out again");
                check(parent.getScreenAnchor() == ScreenAnchor.TOP_RIGHT, "Passive correction deleted the screen anchor");
            });
            context.takeScreenshot("chat-resized-snapped-widget");
            // A too-wide group must remain selectable; its saved snap should recover on shrink.
            context.runOnClient(client -> { ChatDock.settings(key).width = 600; ConfigManager.save(); });
            context.waitTicks(3);
            context.runOnClient(client -> {
                checkInside(child(), client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
                check(child().getSnapTargetKey().equals(key), "Oversized group lost its saved relation");
                ChatDock.settings(key).width = 200; ConfigManager.save();
            });
            context.waitTicks(3);
            context.runOnClient(client -> check(child().getLeft() == ChatDock.widget(key).getRight() + 5,
                    "Shrinking chat did not restore the saved corner relation"));
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); renderer.setSettings(false); renderer.setEditing(false);
                ConfigManager.restoreSnapshot(original); renderer.refreshLayout();
                client.options.guiScale().set(oldScale); client.getWindow().setWindowed(oldWidth, oldHeight); client.resizeGui();
                client.gui.hud.getChat().clearMessages(false);
            });
        }
    }
    private static AbstractWidget child() {
        return (AbstractWidget) Client.getInstance().getHudRenderer().getWidgets().stream()
                .filter(w -> w instanceof ru.wilyfox.client.hud.widget.EstimatedTpsWidget).findFirst().orElseThrow();
    }
    private static void checkInside(AbstractWidget widget, int width, int height) {
        check(widget.getLeft() >= 0 && widget.getTop() >= 0 && widget.getRight() <= width && widget.getBottom() <= height,
                "Widget escaped the screen: " + widget.getConfigKey());
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
