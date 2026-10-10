package ru.wilyfox.client.hud.menu;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.HudRenderer;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetCatalog;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.widget.HudBlur;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public final class HudUndoClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
        int oldWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int oldHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        var panel = new HudSettingsPanel();
        var renderer = new HudRenderer(panel);
        var widget = new TestWidget();
        var screen = context.computeOnClient(client -> new TestScreen(renderer, panel));
        String[] layoutId = new String[1];
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.getWindow().setWindowed(1280, 720); client.resizeGui();
                ConfigManager.get().mainLayout.widgets.clear();
                ConfigManager.get().mainLayout.widgets.add(WidgetCatalog.BOSS_TIMERS.key());
                ConfigManager.get().locationLayouts.clear(); ConfigManager.get().widgetLayouts.clear();
                ConfigManager.get().bossWidget.maxBosses = 10;
                ConfigManager.layoutChanged();
                renderer.registerWidget(widget); renderer.setEditing(true);
                client.gui.setScreen(screen);
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, 0)); // Hide the library.
                widget.setStartX(300); widget.setStartY(80);
                move(screen, 300, 80, 325, 100);
                expect(widget.getStartX() == 325 && widget.getStartY() == 100, "Widget did not move");
                undo(screen);
                expect(widget.getStartX() == 300 && widget.getStartY() == 80, "Undo did not restore the first unsaved position");
                move(screen, 300, 80, 325, 100);
                for (int i = 0; i < 5; i++) renderer.onMouseScrolled(330, 105, 1, true, false);
                expect(Math.abs(widget.getScale() - 1.5f) < .001f, "Wheel scale did not change");
                screen.keyReleased(new KeyEvent(GLFW.GLFW_KEY_LEFT_CONTROL, 0, 0));
                undo(screen);
                expect(widget.getScale() == 1 && widget.getStartX() == 325, "Scale gesture was not one action");
                undo(screen);
                expect(widget.getStartX() == 300 && widget.getStartY() == 80, "Scale wheel events flooded the history");
                screen.mouseClicked(mouse(305, 85, 1), false); screen.mouseReleased(mouse(305, 85, 1));
                expect(renderer.isSettingsOpen(), "Widget context did not open");
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                dragNumber(screen, component(panel, "Max bosses"), 28);
                expect(ConfigManager.get().bossWidget.maxBosses == 17, "Numeric drag did not change the value");
                undo(screen);
                expect(ConfigManager.get().bossWidget.maxBosses == 10, "Undo must restore the initial numeric value");
                expectSaved(10);
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                dragNumber(screen, component(panel, "Max bosses"), 12);
                expect(ConfigManager.get().bossWidget.maxBosses == 13, "Settings callbacks were stale after undo");
                undo(screen);
                expect(ConfigManager.get().bossWidget.maxBosses == 10, "Second drag did not undo independently");
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                var number = component(panel, "Max bosses");
                int x = number.getX() + number.getWidth() - 35, y = number.getY() + 11;
                screen.mouseClicked(mouse(x, y, 0), false);
                screen.mouseDragged(mouse(x + 20, y, 0), 20, 0);
                undo(screen); // Undo even before the mouse button is released.
                screen.mouseReleased(mouse(x + 20, y, 0));
                expect(ConfigManager.get().bossWidget.maxBosses == 10, "Late release overwrote the undone number");
                renderer.setSettings(false);
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_DELETE, 0, 0));
                expect(!ConfigManager.get().mainLayout.widgets.contains(widget.getConfigKey()), "Delete did not remove the widget");
                undo(screen);
                expect(ConfigManager.get().mainLayout.widgets.contains(widget.getConfigKey()), "Undo did not restore membership");
                expect(renderer.getLayoutWidgets().contains(widget), "Restored widget is missing at runtime");
                // A layout button mutates several fields within one mouse gesture.
                screen.mouseClicked(mouse(600, 300, 0), false);
                String layout = renderer.createLayout();
                layoutId[0] = layout;
                screen.mouseReleased(mouse(600, 300, 0));
                expect(ConfigManager.get().locationLayouts.containsKey(layout), "Layout did not get created");
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                click(screen, component(panel, "Name"));
                renderer.onCharTyped('X', 0);
                undo(screen); // Commit and undo the draft, without reapplying an old callback.
                expect(ConfigManager.get().locationLayouts.get(layoutId[0]).name.equals("Layout 1"), "Name draft overwrote the restored config");
                undo(screen);
                expect(ConfigManager.get().locationLayouts.isEmpty() && renderer.getSelectedLayout().isEmpty(), "Undo did not restore the main layout");
                renderer.setSettings(true); renderer.setEditing(false);
                setField(panel, "activeCategory", SettingsCategory.RENDER);
                setField(panel, "scrollOffset", 10000);
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                var number = component(panel, "Extra chat history");
                int before = ConfigManager.get().render.extraChatHistoryLines;
                dragNumber(screen, number, 8);
                expect(ConfigManager.get().render.extraChatHistoryLines == before + 100, "General numeric setting did not change");
                undo(screen);
                expect(ConfigManager.get().render.extraChatHistoryLines == before, "General settings did not undo");
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                var toggle = component(panel, "Auto thx");
                boolean before = ConfigManager.get().render.autoThanks;
                click(screen, toggle);
                expect(ConfigManager.get().render.autoThanks != before, "General toggle did not change");
                undo(screen);
                expect(ConfigManager.get().render.autoThanks == before, "General toggle did not undo");
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                click(screen, component(panel, "Auto thx"));
                boolean changed = ConfigManager.get().render.autoThanks;
                client.gui.setScreen(null); // Ends the session and its history.
                renderer.setSettings(true); client.gui.setScreen(screen);
                undo(screen);
                expect(ConfigManager.get().render.autoThanks == changed, "Closed menu retained its undo history");
            });
            System.out.println("HUD undo: movement, grouped scaling, numeric drags, membership, layouts, general settings and session lifetime passed");
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); renderer.setEditing(false); renderer.setSettings(false);
                ConfigManager.restoreSnapshot(original);
                client.options.guiScale().set(oldScale);
                client.getWindow().setWindowed(oldWidth, oldHeight); client.resizeGui();
            });
        }
    }

    private static void undo(HudEditingScreen screen) {
        expect(screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL)), "Ctrl+Z was not handled");
    }
    private static void move(HudEditingScreen screen, int x, int y, int targetX, int targetY) {
        screen.mouseClicked(mouse(x + 5, y + 5, 0), false);
        screen.mouseDragged(mouse(targetX + 5, targetY + 5, 0), targetX - x, targetY - y);
        screen.mouseReleased(mouse(targetX + 5, targetY + 5, 0));
    }
    private static void dragNumber(HudEditingScreen screen, SettingsComponent number, int distance) {
        int x = number.getX() + number.getWidth() - 35, y = number.getY() + 11;
        screen.mouseClicked(mouse(x, y, 0), false);
        screen.mouseDragged(mouse(x + 4, y, 0), 4, 0);
        screen.mouseDragged(mouse(x + distance, y, 0), distance - 4, 0);
        screen.mouseReleased(mouse(x + distance, y, 0));
    }
    private static void click(HudEditingScreen screen, SettingsComponent component) {
        int x = component.getX() + component.getWidth() - 15, y = component.getY() + 11;
        screen.mouseClicked(mouse(x, y, 0), false); screen.mouseReleased(mouse(x, y, 0));
    }
    private static SettingsComponent component(HudSettingsPanel panel, String label) {
        List<SettingsComponent> components;
        if (panel.isContextSettings()) components = (List<SettingsComponent>) field(panel, "widgetComponents");
        else components = ((Map<SettingsCategory, List<SettingsComponent>>) field(panel, "componentsByCategory"))
                .get((SettingsCategory) field(panel, "activeCategory"));
        return components.stream().filter(c -> label.equals(field(c, SettingsComponent.class, "label"))).findFirst().orElseThrow();
    }
    private static Object field(Object target, String name) { return field(target, target.getClass(), name); }
    private static Object field(Object target, Class<?> type, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void setField(Object target, String name, Object value) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static MouseButtonEvent mouse(double x, double y, int button) { return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0)); }
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void expectSaved(int value) {
        try {
            var saved = JsonParser.parseString(Files.readString(FabricLoader.getInstance().getConfigDir().resolve("froghelper.json")));
            expect(saved.getAsJsonObject().getAsJsonObject("bossWidget").get("maxBosses").getAsInt() == value, "Undone value was not saved");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    private static final class TestWidget extends AbstractWidget {
        TestWidget() { super(300, 80, HudLayer.CONTENT); setConfigKey(WidgetCatalog.BOSS_TIMERS.key()); width = 20; height = 12; }
        @Override public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {}
    }
    private static final class TestScreen extends HudEditingScreen {
        private final HudRenderer renderer;
        private final HudSettingsPanel panel;
        TestScreen(HudRenderer renderer, HudSettingsPanel panel) { super(renderer); this.renderer = renderer; this.panel = panel; }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) {
            HudBlur.beginFrame(graphics);
            if (renderer.isSettingsOpen()) panel.render(graphics, x, y);
        }
    }
}

