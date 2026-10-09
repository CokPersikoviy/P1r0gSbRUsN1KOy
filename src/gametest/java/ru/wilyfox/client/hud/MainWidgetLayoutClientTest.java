package ru.wilyfox.client.hud;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.HudConfig;
import ru.wilyfox.client.hud.config.HudConfigCodec;
import ru.wilyfox.client.hud.config.WidgetCatalog;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.widget.Widget;
import ru.wilyfox.client.hud.widget.WidgetCorner;

import java.nio.file.Files;

public final class MainWidgetLayoutClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        Gson gson = new Gson();
        HudConfig original = context.computeOnClient(client -> gson.fromJson(gson.toJson(ConfigManager.get()), HudConfig.class));
        int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
        int oldWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int oldHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        HudRenderer renderer = Client.getInstance().getHudRenderer();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                for (Widget widget : renderer.getLayoutWidgets()) renderer.removeWidget(widget);
                ConfigManager.get().widgetLayouts.clear();
                client.options.guiScale().set(2);
                client.getWindow().setWindowed(854, 480);
                client.resizeGui();
                client.gui.setScreen(new HudEditingScreen(renderer));
                renderer.setSettings(false);
                renderer.setEditing(true);
                expect(renderer.getRegisteredWidgetCount() == WidgetCatalog.values().length, "Library must contain all widgets");
                expect(renderer.getLayoutWidgetCount() == 0, "Fresh layout must be empty");
            });
            context.waitTicks(3);
            context.takeScreenshot("widgets-main-layout-empty");
            context.runOnClient(client -> {
                renderer.onMousePressed(132, 72, 0);
                Widget boss = find(renderer, "BossHudWidget");
                expect(renderer.isWidgetAdded(boss), "Sidebar add must insert the widget");
                expect(renderer.getSelectedWidget() == boss, "Added widget must be selected");
                expect(!renderer.addWidget(boss), "Adding twice must not duplicate the widget");
                expect(boss.isVisible(), "Added widget must preview without server data");
                int x = boss.getStartX(), y = boss.getStartY();
                var screen = (HudEditingScreen) client.gui.screen();
                screen.mouseClicked(new MouseButtonEvent(x + 5, y + 5, new MouseButtonInfo(1, 0)), false);
                expect(renderer.isSettingsOpen() && !renderer.isWidgetDragging(), "RMB must open settings without dragging");
                renderer.renderSettingsOverlay(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO);
                expect(renderer.onKeyPressed(GLFW.GLFW_KEY_DELETE, 0, 0) == false && renderer.isWidgetAdded(boss),
                        "Delete in the settings panel must not remove the widget underneath");
                var panel = (ru.wilyfox.client.hud.menu.HudSettingsPanel) field(renderer, "settingsPanel");
                var controls = (java.util.List<?>) field(panel, "widgetComponents");
                var scale = (ru.wilyfox.client.hud.menu.SettingsComponent) controls.getFirst();
                var scalableBoss = (AbstractWidget) boss;
                float originalScale = scalableBoss.getScale();
                double valueX = scale.getX() + scale.getWidth() - 35, valueY = scale.getY() + 11;
                renderer.onMousePressed(valueX, valueY, 0);
                renderer.onMouseDragged(valueX + 8, -1000, 427, 240, 0);
                renderer.onMouseReleased(0, 427, 240, valueX + 8, -1000);
                expect(Math.abs(scalableBoss.getScale() - originalScale - .02f) < .0001f, "Widget scale did not scrub relatively");
                try {
                    var saved = JsonParser.parseString(Files.readString(FabricLoader.getInstance().getConfigDir().resolve("froghelper.json")));
                    expect(Math.abs(saved.getAsJsonObject().getAsJsonObject("widgetLayouts").getAsJsonObject("BossHudWidget")
                            .get("scale").getAsFloat() - scalableBoss.getScale()) < .0001f, "Scale was not saved on release");
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
                scalableBoss.setScale(originalScale);
                ConfigManager.saveWidgetLayout(scalableBoss);
                expect(renderer.onKeyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0) && !renderer.isSettingsOpen() && renderer.isEditing(),
                        "Escape must return to the same widget editor");
                renderer.onMousePressed(x + 5, y + 5, 1);
                renderer.renderSettingsOverlay(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO);
                renderer.onMousePressed(426, 239, 0);
                expect(!renderer.isSettingsOpen() && !renderer.isWidgetDragging() && boss.getStartX() == x && boss.getStartY() == y,
                        "Closing the modal with an outside click moved the underlying widget");
                screen.mouseClicked(mouse(x + 5, y + 5), false);
                screen.mouseDragged(mouse(x + 16, y + 18), 11, 13);
                screen.mouseReleased(mouse(x + 16, y + 18));
                expect(boss.getStartX() == x + 11 && boss.getStartY() == y + 13, "Drag must update without a tick");

                AbstractWidget child = (AbstractWidget) find(renderer, "LevelProgressWidget");
                renderer.addWidget(child);
                child.setWidgetSnap("BossHudWidget", WidgetCorner.TOP_LEFT, WidgetCorner.BOTTOM_LEFT);
                int childX = child.getStartX(), childY = child.getStartY();
                renderer.removeWidget(boss);
                expect(!child.hasWidgetSnap(), "Removing a parent must detach its child");
                expect(child.getStartX() == childX && child.getStartY() == childY, "Removal must retain child's position");
                expect(!boss.isVisible(), "Removed widget must not leak into previews");
                renderer.addWidget(boss);
                expect(boss.getStartX() == x + 11 && boss.getStartY() == y + 13, "Re-add must preserve placement");

                renderer.onMousePressed(25, 45, 0);
                for (int codePoint : "quests".codePoints().toArray()) renderer.onCharTyped(codePoint, 0);
                renderer.onMousePressed(132, 72, 0);
                Widget quests = find(renderer, "FishingQuestsWidget");
                expect(renderer.isWidgetAdded(quests), "Search must find an offscreen widget");
                renderer.onMousePressed(25, 45, 0);
                renderer.onKeyPressed(GLFW.GLFW_KEY_DELETE, 0, 0);
                expect(renderer.isWidgetAdded(quests), "Delete in search must not delete a widget");
                renderer.onKeyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                renderer.onKeyPressed(GLFW.GLFW_KEY_DELETE, 0, 0);
                expect(!renderer.isWidgetAdded(quests), "Delete must remove selected widget");
            });
            context.waitTicks(3);
            context.takeScreenshot("widgets-main-layout-added");
            context.runOnClient(client -> {
                for (Widget widget : renderer.getLayoutWidgets()) renderer.removeWidget(widget);
                ConfigManager.get().widgetLayouts.clear();
                renderer.onMousePressed(25, 45, 0);
                renderer.onKeyPressed(GLFW.GLFW_KEY_DELETE, 0, 0);
                for (int codePoint : "protocol".codePoints().toArray()) renderer.onCharTyped(codePoint, 0);
                renderer.onMousePressed(132, 72, 0);
                expect(renderer.isWidgetAdded(find(renderer, "ProtocolGraphWidget")), "Sidebar must add the packet graph");
            });
            context.waitTicks(3);
            context.takeScreenshot("widgets-protocol-graph");
            context.runOnClient(client -> {
                long started = System.nanoTime();
                // Reproduce rapidly adding the bottom entries next to the oversized packet graph,
                // then fill the canvas so placement has to tolerate a layout with no free space.
                renderer.addWidget(find(renderer, "PopUpsWidget"));
                renderer.addWidget(find(renderer, "LevelProgressWidget"));
                for (Widget widget : renderer.getWidgets()) renderer.addWidget(widget);
                expect(renderer.getLayoutWidgetCount() == renderer.getRegisteredWidgetCount(), "Rapid add lost a widget");
                long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                System.out.println("Adding all widgets around Protocol Graph took " + elapsedMs + " ms");
                expect(elapsedMs < 5000, "Adding widgets blocked the client for over five seconds");
                for (Widget widget : renderer.getWidgets()) {
                    expect(renderer.openWidgetSettings(widget), "Missing context menu: " + widget.getDisplayName());
                    var panel = (ru.wilyfox.client.hud.menu.HudSettingsPanel) field(renderer, "settingsPanel");
                    expect(field(panel, "settingsWidget") == widget, "Menu targets another widget");
                    expect(!((java.util.List<?>) field(panel, "widgetComponents")).isEmpty(), "Missing scale control");
                    renderer.setSettings(false);
                }
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                for (Widget widget : renderer.getLayoutWidgets()) renderer.removeWidget(widget);
                AbstractWidget boss = (AbstractWidget) find(renderer, "BossHudWidget");
                renderer.addWidget(boss);
                boss.setScreenAnchor(null);
                boss.clearWidgetSnap();
                boss.setStartX(20);
                boss.setStartY(100);
                renderer.onMousePressed(25, 45, 0);
                renderer.onKeyPressed(GLFW.GLFW_KEY_DELETE, 0, 0);
                for (int codePoint : "boss timers".codePoints().toArray()) renderer.onCharTyped(codePoint, 0);
                renderer.onMousePressed(90, 72, 0);
                renderer.onMouseReleased(0, 427, 240, 90, 72);
                expect(boss.getStartX() == 20 && boss.getStartY() == 100,
                        "Selecting a library entry must not move the covered widget");
                renderer.onMousePressed(90, 72, 0);
                renderer.onMouseDragged(90, 135, 427, 240, 0);
                expect(renderer.isWidgetDragging(), "A covered widget must be draggable from its library entry");
                expect(boss.getStartX() != 20 || boss.getStartY() != 100, "Library drag did not reposition the widget");
                var overlay = new GuiRenderState();
                renderer.renderSettingsOverlay(new GuiGraphicsExtractor(client, overlay, 0, 0), DeltaTracker.ZERO);
                expect(elementCount(overlay) == 0, "Sidebar must disappear while positioning a widget underneath it");
            });
            context.waitTicks(2);
            context.takeScreenshot("widgets-library-drag");
            context.runOnClient(client -> {
                renderer.onMouseReleased(0, 427, 240, 90, 135);
                expect(!renderer.isWidgetDragging(), "Library drag must end on release");
                var overlay = new GuiRenderState();
                renderer.renderSettingsOverlay(new GuiGraphicsExtractor(client, overlay, 0, 0), DeltaTracker.ZERO);
                expect(elementCount(overlay) > 0, "Sidebar must return after dragging");
                renderer.onKeyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                overlay.reset();
                renderer.renderSettingsOverlay(new GuiGraphicsExtractor(client, overlay, 0, 0), DeltaTracker.ZERO);
                expect(elementCount(overlay) == 0, "Tab must fully hide the sidebar");
                Widget boss = find(renderer, "BossHudWidget");
                int x = boss.getStartX(), y = boss.getStartY();
                renderer.onMousePressed(x + 5, y + 5, 0);
                renderer.onMouseDragged(x + 16, y + 18, 427, 240, 0);
                renderer.onMouseReleased(0, 427, 240, x + 16, y + 18);
                expect(boss.getStartX() == x + 11 && boss.getStartY() == y + 13,
                        "Hidden sidebar still intercepted canvas dragging");
                renderer.onKeyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                overlay.reset();
                renderer.renderSettingsOverlay(new GuiGraphicsExtractor(client, overlay, 0, 0), DeltaTracker.ZERO);
                expect(elementCount(overlay) > 0, "Tab must restore the sidebar");
            });
            context.waitTicks(2);
            context.takeScreenshot("widgets-sidebar-counter");
            context.runOnClient(client -> {
                var library = new ru.wilyfox.client.hud.internal.HudWidgetSidebar(renderer);
                library.mousePressed(90, 72, 1);
                expect(renderer.isSettingsOpen() && renderer.getSelectedWidget() == find(renderer, "BossHudWidget"),
                        "RMB in the library must open settings for a covered widget");
            });
            context.waitTicks(2);
            context.takeScreenshot("widgets-boss-settings");
            context.runOnClient(client -> {
                renderer.onKeyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                var map = find(renderer, "DungeonMapWidget");
                renderer.addWidget(map);
                renderer.openWidgetSettings(map);
            });
            context.waitTicks(2);
            context.takeScreenshot("widgets-map-settings");
            context.runOnClient(client -> renderer.onKeyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
            context.runOnClient(client -> {
                for (Widget widget : renderer.getLayoutWidgets()) renderer.removeWidget(widget);
                try {
                    var saved = JsonParser.parseString(Files.readString(FabricLoader.getInstance().getConfigDir().resolve("froghelper.json")));
                    expect(HudConfigCodec.decode(gson, saved).mainLayout.widgets.isEmpty(), "Reload must retain empty layout");
                    expect(saved.getAsJsonObject().getAsJsonObject("widgetLayouts").has("BossHudWidget"), "Removed widget must keep placement");
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
            });
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                for (Widget widget : renderer.getLayoutWidgets()) renderer.removeWidget(widget);
                ConfigManager.get().widgetLayouts = original.widgetLayouts;
                ConfigManager.get().lastWindowWidth = original.lastWindowWidth;
                ConfigManager.get().lastWindowHeight = original.lastWindowHeight;
                client.options.guiScale().set(oldScale);
                client.getWindow().setWindowed(oldWidth, oldHeight);
                client.resizeGui();
                for (Widget widget : renderer.getWidgets()) {
                    if (!(widget instanceof AbstractWidget placed)) continue;
                    var saved = original.widgetLayouts.get(placed.getConfigKey());
                    placed.clearWidgetSnap();
                    placed.setScreenAnchor(null);
                    if (saved != null) {
                        if (saved.x != null) placed.setStartX(saved.x);
                        if (saved.y != null) placed.setStartY(saved.y);
                        if (saved.scale != null) placed.setScale(saved.scale);
                        placed.setScreenAnchor(saved.anchor);
                        if (saved.snapTarget != null) placed.setWidgetSnap(saved.snapTarget, saved.snapOwnCorner, saved.snapTargetCorner);
                    }
                    if (original.mainLayout.widgets.contains(placed.getConfigKey())) renderer.addWidget(widget);
                }
                ConfigManager.get().mainLayout = original.mainLayout;
                ConfigManager.layoutChanged();
                ConfigManager.get().widgetLayouts = original.widgetLayouts;
                ConfigManager.save();
            });
        }
    }

    private static int elementCount(GuiRenderState state) {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        state.forEachElement(element -> count.incrementAndGet(), GuiRenderState.TraverseRange.ALL);
        return count.get();
    }
    private static Widget find(HudRenderer renderer, String key) {
        return renderer.getWidgets().stream().filter(widget -> widget.getClass().getSimpleName().equals(key)).findFirst().orElseThrow();
    }
    private static Object field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static MouseButtonEvent mouse(double x, double y) { return new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)); }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
