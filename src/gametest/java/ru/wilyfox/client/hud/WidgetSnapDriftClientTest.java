package ru.wilyfox.client.hud;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.booster.BoosterStore;
import ru.wilyfox.client.chat.ChatDock;
import ru.wilyfox.client.chat.ChatTab;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.widget.BoostersWidget;
import ru.wilyfox.client.hud.widget.WidgetCorner;

import java.util.LinkedHashMap;
import java.util.List;

public final class WidgetSnapDriftClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
        int oldWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int oldHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        var renderer = Client.getInstance().getHudRenderer();
        int[] baseline = new int[4];
        String chatKey = ChatDock.key(ChatTab.CLAN);
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.guiScale().set(2); client.getWindow().setWindowed(1280, 720); client.resizeGui();
                var config = ConfigManager.get();
                config.locationLayouts.clear(); config.widgetLocations.clear(); config.mainLayout.widgets.clear();
                config.widgetLayouts.clear(); config.render.nativeRenderer = true;
                config.mainLayout.widgets.add("BoostersWidget"); config.mainLayout.widgets.add("VisibilityStatusWidget");
                ConfigManager.layoutChanged(); renderer.refreshLayout();
                editor();
                // Empty widgets should reserve the same geometry whether their placeholders are drawn or hidden.
                var sizes = new LinkedHashMap<AbstractWidget, List<Integer>>();
                for (var widget : renderer.getWidgets()) if (widget instanceof AbstractWidget placed) {
                    sizes.put(placed, List.of(widget.getWidth(), widget.getHeight()));
                }
                client.gui.setScreen(null);
                for (var entry : sizes.entrySet()) check(entry.getValue().equals(List.of(entry.getKey().getWidth(), entry.getKey().getHeight())),
                        "Editor-only geometry changed: " + entry.getKey().getConfigKey());
                // Partial booster data must not add fake columns exclusively in the editor either.
                var store = new BoosterStore(); store.replace(BoosterStore.Kind.MONEY,
                        List.of(new BoosterStore.ProtocolEntry(2, 60_000)));
                var probe = new BoostersWidget(0, 0, ru.wilyfox.client.hud.layer.HudLayer.CONTENT, store);
                int w = probe.getWidth(), h = probe.getHeight(); editor();
                check(probe.getWidth() == w && probe.getHeight() == h, "Partial booster geometry changed in editor");

                var parent = widget("BoostersWidget"); var child = widget("VisibilityStatusWidget");
                parent.setScreenAnchor(null); parent.clearWidgetSnap(); parent.setScale(1);
                parent.setStartX(100); parent.setStartY(100);
                child.setScreenAnchor(null); child.setScale(1);
                child.setWidgetSnap(parent.getConfigKey(), WidgetCorner.BOTTOM_LEFT, WidgetCorner.TOP_RIGHT);
                ConfigManager.saveWidgetLayouts(List.of(parent, child));
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                var parent = widget("BoostersWidget"); var child = widget("VisibilityStatusWidget");
                baseline[0] = parent.getLeft(); baseline[1] = parent.getTop();
                baseline[2] = child.getLeft(); baseline[3] = child.getTop();
                check(!parent.hasWidgetSnap() && child.getSnapTargetKey().equals(parent.getConfigKey()), "Valid root was reoriented");
            });
            context.takeScreenshot("snap-group-editor");
            for (int cycle = 0; cycle < 6; cycle++) {
                context.runOnClient(client -> client.gui.setScreen(null));
                context.waitTicks(2);
                context.runOnClient(client -> checkBaseline(baseline));
                if (cycle == 0) context.takeScreenshot("snap-group-gameplay");
                context.runOnClient(client -> editor());
                context.waitTicks(2);
                context.runOnClient(client -> checkBaseline(baseline));
            }
            // A free chat parent must be tracked outside the editor and return after a temporary wider width.
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                ConfigManager.get().mainLayout.widgets.clear(); ConfigManager.get().widgetLayouts.clear();
                ConfigManager.get().mainLayout.widgets.add(chatKey); ConfigManager.get().mainLayout.widgets.add("EstimatedTpsWidget");
                var settings = ChatDock.settings(chatKey);
                settings.deleted = false; settings.detached = true; settings.width = 100;
                ConfigManager.get().estimatedTps.enabled = false;
                ConfigManager.layoutChanged(); renderer.refreshLayout();
                var parent = ChatDock.widget(chatKey); var child = widget("EstimatedTpsWidget");
                parent.setScreenAnchor(null); parent.clearWidgetSnap(); parent.setScale(1);
                parent.setStartX(350); parent.setStartY(130);
                child.setScreenAnchor(null); child.setScale(1);
                child.setWidgetSnap(chatKey, WidgetCorner.TOP_LEFT, WidgetCorner.TOP_RIGHT);
                ConfigManager.saveWidgetLayouts(List.of(parent, child));
            });
            context.waitTicks(10);
            for (int cycle = 0; cycle < 3; cycle++) {
                context.runOnClient(client -> { ChatDock.settings(chatKey).width = 381; ConfigManager.save(); });
                context.waitTicks(10);
                context.runOnClient(client -> {
                    check(widget("EstimatedTpsWidget").getRight() <= client.getWindow().getGuiScaledWidth(), "Passive growth escaped screen");
                    check(ChatDock.widget(chatKey).getLeft() < 350, "Free parent size was not tracked outside editor");
                    ChatDock.settings(chatKey).width = 100; ConfigManager.save();
                });
                context.waitTicks(10);
                context.runOnClient(client -> {
                    check(ChatDock.widget(chatKey).getLeft() == 350, "Temporary clamp permanently moved the free root");
                    check(widget("EstimatedTpsWidget").getLeft() == 455, "Child drifted after its parent shrank");
                });
            }
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); renderer.setSettings(false); renderer.setEditing(false);
                ConfigManager.restoreSnapshot(original); renderer.refreshLayout();
                client.options.guiScale().set(oldScale); client.getWindow().setWindowed(oldWidth, oldHeight); client.resizeGui();
            });
        }
    }
    private static void editor() {
        var renderer = Client.getInstance().getHudRenderer();
        net.minecraft.client.Minecraft.getInstance().gui.setScreen(new HudEditingScreen(renderer));
        renderer.setEditing(true); renderer.onKeyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
    }
    private static AbstractWidget widget(String key) {
        return (AbstractWidget) Client.getInstance().getHudRenderer().getWidgets().stream()
                .filter(w -> w instanceof AbstractWidget placed && key.equals(placed.getConfigKey())).findFirst().orElseThrow();
    }
    private static void checkBaseline(int[] expected) {
        var parent = widget("BoostersWidget"); var child = widget("VisibilityStatusWidget");
        check(parent.getLeft() == expected[0] && parent.getTop() == expected[1]
                && child.getLeft() == expected[2] && child.getTop() == expected[3], "Editor/gameplay transition moved the group");
        check(!parent.hasWidgetSnap() && child.getSnapTargetKey().equals(parent.getConfigKey()), "Transition reversed the snap dependency");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
