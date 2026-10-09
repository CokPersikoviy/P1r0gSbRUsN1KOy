package ru.wilyfox.client.hud;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.menu.*;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.protocol.DwGameLocation;
import java.nio.file.Files;
import java.util.*;

public final class LocationLayoutsClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        Gson gson = HudConfigCodec.createGson();
        HudConfig original = context.computeOnClient(client -> gson.fromJson(gson.toJson(ConfigManager.get()), HudConfig.class));
        var renderer = Client.getInstance().getHudRenderer();
        Object state = field(null, DiamondWorldProtocolClient.class, "STATE");
        Object oldLocation = field(state, state.getClass(), "currentGameLocation");
        int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
        int oldWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int oldHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        var id = new String[1];
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.guiScale().set(2); client.getWindow().setWindowed(1280, 720); client.resizeGui();
                ConfigManager.get().mainLayout.widgets.clear(); ConfigManager.get().widgetLayouts.clear();
                ConfigManager.get().locationLayouts.clear(); ConfigManager.get().widgetLocations.clear();
                ConfigManager.layoutChanged();
                location(state, "market");
                client.gui.setScreen(new HudEditingScreen(renderer)); renderer.setEditing(true); renderer.selectLayout("");
                var boss = widget(renderer, "BossHudWidget");
                renderer.addWidget(boss); boss.setScreenAnchor(null); boss.clearWidgetSnap();
                boss.setStartX(360); boss.setStartY(30); boss.setScale(1f); ConfigManager.saveWidgetLayout(boss);
                expect(!renderer.deleteLayout("") && !renderer.openLayoutSettings(), "Main layout properties must be locked");
                renderer.onMousePressed(25, 18, 0); // layout dropdown
            });
            context.waitTicks(2); context.takeScreenshot("layouts-selector");
            context.runOnClient(client -> {
                renderer.onMousePressed(30, 67, 0); // New layout after Main
                id[0] = renderer.getSelectedLayout();
                expect(!id[0].isEmpty() && renderer.isSettingsOpen(), "Creating a layout from the sidebar failed");
                expect(renderer.getLayoutWidgetCount() == 0, "New layout must start empty");
                overlay(renderer, client);
                var panel = panel(renderer);
                var visibility = controls(panel).stream().filter(LocationSettingsComponent.class::isInstance).map(LocationSettingsComponent.class::cast).findFirst().orElseThrow();
                panel.mousePressed(visibility.getX() + 10, visibility.getY() + 10, 0);
                overlay(renderer, client);
                panel.mousePressed(visibility.getX() + 15, visibility.getY() + 80, 0); // All fishing locations
                expect(ConfigManager.get().locationLayouts.get(id[0]).locationVisibility.equals(Set.of("#fishing")), "Location picker did not store a protocol selector");
                renderer.onKeyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); overlay(renderer, client);
                var name = controls(panel).getFirst();
                panel.mousePressed(name.getX() + name.getWidth() - 30, name.getY() + 10, 0);
                for (int i = 0; i < 30; i++) panel.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
                for (int letter : "Fishing".codePoints().toArray()) panel.charTyped(letter, 0);
                panel.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                expect(ConfigManager.get().locationLayouts.get(id[0]).name.equals("Fishing"), "Deleting the old name completely and renaming failed");
                panel.mousePressed(visibility.getX() + 10, visibility.getY() + 10, 0); overlay(renderer, client);
            });
            context.waitTicks(2); context.takeScreenshot("layouts-fishing-settings");
            context.runOnClient(client -> {
                // Escape closes the expanded picker, then returns to the editor.
                renderer.onKeyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); renderer.onKeyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                expect(!renderer.isSettingsOpen(), "Layout modal did not close");
                var boss = widget(renderer, "BossHudWidget"); renderer.addWidget(boss);
                boss.setScreenAnchor(null); boss.clearWidgetSnap(); boss.setStartX(270); boss.setStartY(110); boss.setScale(1.6f);
                ConfigManager.saveWidgetLayout(boss);
                renderer.addWidget(widget(renderer, "FishingQuestsWidget"));
                renderer.openWidgetSettings(boss); overlay(renderer, client);
                var visible = (LocationSettingsComponent) controls(panel(renderer)).get(1);
                panel(renderer).mousePressed(visible.getX() + 10, visible.getY() + 10, 0); overlay(renderer, client);
                panel(renderer).mousePressed(visible.getX() + 15, visible.getY() + 80, 0);
                expect(ConfigManager.getWidgetLocations("BossHudWidget").locationVisibility.equals(Set.of("#fishing")), "Widget location allowlist failed");
            });
            context.waitTicks(2); context.takeScreenshot("widgets-location-visibility");
            context.runOnClient(client -> {
                var panel = panel(renderer);
                renderer.onKeyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); overlay(renderer, client);
                var hidden = (LocationSettingsComponent) controls(panel).get(2);
                panel.mousePressed(hidden.getX() + 10, hidden.getY() + 10, 0); overlay(renderer, client);
                panel.mousePressed(hidden.getX() + 15, hidden.getY() + 99, 0); // All bosses, the second group
                expect(ConfigManager.getWidgetLocations("BossHudWidget").locationVisibility.isEmpty(), "Hidden and visible lists are not exclusive");
                expect(ConfigManager.getWidgetLocations("BossHudWidget").locationHidden.equals(Set.of("#boss")), "Widget denylist failed");
                renderer.setSettings(false); renderer.selectLayout("");
                var boss = widget(renderer, "BossHudWidget");
                expect(boss.getStartX() == 360 && boss.getStartY() == 30 && boss.getScale() == 1f, "Editing main inherited a location override");
                client.gui.setScreen(null);
                location(state, "bay"); renderer.refreshLayout();
                expect(renderer.getLayoutWidgetCount() == 2 && renderer.getLayoutWidgets().stream().filter(w -> w == boss).count() == 1,
                        "Location overlays cloned a widget or lost an additional widget");
                expect(boss.getStartX() == 270 && boss.getStartY() == 110 && boss.getScale() == 1.6f, "Runtime did not apply the fishing placement");
                client.getWindow().setWindowed(1600, 900); client.resizeGui();
                renderer.render(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO);
                expect(boss.getStartX() == 338 && boss.getStartY() == 138, "Active profile did not resize from its own fractions");
                location(state, "market"); renderer.refreshLayout();
                expect(boss.getStartX() == 450 && boss.getStartY() == 38, "Main placement was not restored at the new resolution");
                client.getWindow().setWindowed(1280, 720); client.resizeGui();
                renderer.render(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO);
                expect(renderer.getLayoutWidgetCount() == 1 && boss.getStartX() == 360 && boss.getScale() == 1f, "Leaving fishing failed to restore main");
                location(state, "boss_legion"); renderer.refreshLayout();
                expect(!ConfigManager.isWidgetInCurrentLayout("BossHudWidget"), "Denylist does not hide a widget in a runtime boss location");
                location(state, null); renderer.refreshLayout();
                expect(ConfigManager.isWidgetInCurrentLayout("BossHudWidget"), "Unknown locations should keep denylist widgets visible");
                var main = ConfigManager.get().widgetLayouts.get("BossHudWidget");
                expect(main.x == 360 && main.y == 30 && main.scale == 1f, "Runtime switches wrote overrides into main");
                try {
                    var loaded = HudConfigCodec.decode(gson, JsonParser.parseString(Files.readString(FabricLoader.getInstance().getConfigDir().resolve("froghelper.json"))));
                    expect(loaded.locationLayouts.get(id[0]).placements.get("BossHudWidget").scale == 1.6f, "Named placements were not persisted");
                    expect(loaded.widgetLocations.get("BossHudWidget").locationHidden.equals(Set.of("#boss")), "Widget filters were not persisted");
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
                client.gui.setScreen(new HudEditingScreen(renderer)); renderer.setEditing(true); renderer.selectLayout(id[0]);
                expect(renderer.deleteLayout(id[0]), "Deleting a named layout failed");
                expect(renderer.getSelectedLayout().isEmpty() && renderer.getLayoutWidgetCount() == 1 && boss.getScale() == 1f,
                        "Deleting the active layout failed to return to main");
                client.getWindow().setWindowed(854, 480); client.resizeGui();
                renderer.openWidgetSettings(boss); overlay(renderer, client);
                var locations = (LocationSettingsComponent) controls(panel(renderer)).get(1);
                panel(renderer).mousePressed(locations.getX() + 10, locations.getY() + 10, 0); overlay(renderer, client);
                panel(renderer).mouseScrolled(locations.getX() + 20, locations.getY() + 80, -100);
                overlay(renderer, client);
                panel(renderer).mousePressed(locations.getX() + 20, locations.getY() + 80, 0);
                expect(!ConfigManager.getWidgetLocations("BossHudWidget").locationVisibility.isEmpty(), "Last locations became unreachable in a small viewport");
                panel(renderer).mousePressed(locations.getX() + 20, locations.getY() + 34, 0);
                for (int letter : "custom_dw_125".codePoints().toArray()) panel(renderer).charTyped(letter, 0);
                panel(renderer).keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                expect(ConfigManager.getWidgetLocations("BossHudWidget").locationVisibility.contains("custom_dw_125"), "Custom server IDs could not be added");
            });
            context.waitTicks(2); context.takeScreenshot("widgets-location-small-screen");
        } finally {
            context.runOnClient(client -> {
                renderer.setSettings(false); client.gui.setScreen(null); renderer.setEditing(false);
                write(null, ConfigManager.class, "CONFIG", original); ConfigManager.setEditorLayout(null);
                write(renderer, HudRenderer.class, "selectedLayout", "");
                write(state, state.getClass(), "currentGameLocation", oldLocation);
                client.options.guiScale().set(oldScale); client.getWindow().setWindowed(oldWidth, oldHeight); client.resizeGui();
                ConfigManager.save(); renderer.refreshLayout();
            });
        }
    }
    private static void overlay(HudRenderer renderer, net.minecraft.client.Minecraft client) {
        renderer.renderSettingsOverlay(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO);
    }
    private static HudSettingsPanel panel(HudRenderer renderer) { return (HudSettingsPanel) field(renderer, HudRenderer.class, "settingsPanel"); }
    @SuppressWarnings("unchecked") private static List<SettingsComponent> controls(HudSettingsPanel panel) { return (List<SettingsComponent>) field(panel, HudSettingsPanel.class, "widgetComponents"); }
    private static AbstractWidget widget(HudRenderer renderer, String key) { return (AbstractWidget) renderer.getWidgets().stream().filter(w -> w.getClass().getSimpleName().equals(key)).findFirst().orElseThrow(); }
    private static void location(Object state, String id) { write(state, state.getClass(), "currentGameLocation", id == null ? null : new DwGameLocation(id)); }
    private static Object field(Object object, Class<?> type, String name) {
        try { var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void write(Object object, Class<?> type, String name, Object value) {
        try { var f = type.getDeclaredField(name); f.setAccessible(true); f.set(object, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
