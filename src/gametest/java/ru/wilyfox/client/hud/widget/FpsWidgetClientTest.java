package ru.wilyfox.client.hud.widget;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.menu.SettingsComponent;
import ru.wilyfox.client.performance.FpsMonitor;

import java.util.ArrayList;
import java.util.List;

public final class FpsWidgetClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        var renderer = Client.getInstance().getHudRenderer();
        var monitor = new FpsMonitor();
        var preview = new FpsWidget(30, 40, HudLayer.CONTENT, monitor);
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                renderer.setSettings(false); renderer.setEditing(false);
                ConfigManager.get().mainLayout.widgets.clear();
                ConfigManager.get().locationLayouts.clear(); ConfigManager.get().widgetLocations.clear();
                ConfigManager.get().render.unclutterWidgets = true;
                ConfigManager.get().render.nativeRenderer = true;
                ConfigManager.get().fpsWidget.horizontal = true;
                ConfigManager.layoutChanged(); renderer.refreshLayout();
                var widget = renderer.getWidgets().stream().filter(w -> w instanceof FpsWidget).findFirst().orElseThrow();
                check(!widget.isVisible(), "FPS was automatically added to the main layout");
                renderer.addWidget(widget);
                check(widget.isVisible(), "Adding FPS did not reveal the widget");
                seed(monitor);
                var text = texts(preview);
                check(text.size() == 3, "FPS metrics missing");
                check(field(text.get(0), "y").equals(field(text.get(2), "y")), "Horizontal FPS wrapped to multiple rows");
                check((int) field(text.get(0), "x") < (int) field(text.get(2), "x"), "Horizontal metrics overlap");
                renderer.setEditing(true);
                check(renderer.openWidgetSettings(widget), "FPS has no context settings");
                renderer.renderSettingsOverlay(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO);
                var panel = field(renderer, "settingsPanel");
                var controls = (List<?>) field(panel, "widgetComponents");
                var toggle = (SettingsComponent) controls.stream().filter(c -> ((SettingsComponent) c).getLabel().equals("Horizontal layout")).findFirst().orElseThrow();
                double x = toggle.getX() + toggle.getWidth() - 20, y = toggle.getY() + 11;
                int rowWidth = preview.getWidth(), rowHeight = preview.getHeight();
                renderer.onMousePressed(x, y, 0);
                renderer.onMouseReleased(0, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), x, y);
                check(!ConfigManager.get().fpsWidget.horizontal, "FPS toggle did not switch to a column");
                text = texts(preview);
                check(field(text.get(0), "x").equals(field(text.get(2), "x")), "Column metrics do not align");
                check((int) field(text.get(0), "y") < (int) field(text.get(2), "y"), "Column metrics overlap");
                check(preview.getWidth() < rowWidth && preview.getHeight() > rowHeight, "FPS bounds did not follow the layout");
                int width = preview.getWidth(), height = preview.getHeight();
                preview.setScale(2);
                check(preview.getWidth() == width * 2 && preview.getHeight() == height * 2, "FPS bounds ignore scale");
                check(texts(preview).getFirst().pose.m00() == 2, "FPS text ignores scale");
                preview.setScale(1);
                renderer.setSettings(false); renderer.setEditing(false);
                client.gui.setScreen(new Preview(preview));
            });
            context.waitTicks(3);
            context.takeScreenshot("fps-widget-column");
            context.runOnClient(client -> ConfigManager.get().fpsWidget.horizontal = true);
            context.waitTicks(3);
            context.takeScreenshot("fps-widget-horizontal");
            context.runOnClient(client -> {
                check(client.isWindowActive(), "Test window must be active to exercise frame sampling");
                check(FpsMonitor.INSTANCE.snapshot(System.nanoTime()).sampleCount() > 0, "Native frames did not reach FPS monitor");
                var widget = renderer.getWidgets().stream().filter(w -> w instanceof FpsWidget).findFirst().orElseThrow();
                renderer.removeWidget(widget);
            });
            context.waitTicks(2);
            context.runOnClient(client -> check(FpsMonitor.INSTANCE.snapshot(System.nanoTime()).sampleCount() == 0,
                    "Removing FPS did not stop and reset measurement"));
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); renderer.setEditing(false); renderer.setSettings(false);
                ConfigManager.restoreSnapshot(original); renderer.refreshLayout(); FpsMonitor.INSTANCE.reset();
            });
        }
    }
    private static void seed(FpsMonitor monitor) {
        long now = System.nanoTime() - 10_550_000_000L;
        monitor.recordFrame(now, true);
        for (int i = 0; i < 1000; i++) monitor.recordFrame(now += i < 990 ? 10_000_000L : i < 999 ? 50_000_000L : 200_000_000L, true);
    }
    private static List<GuiTextRenderState> texts(FpsWidget widget) {
        var state = new GuiRenderState();
        widget.render(new GuiGraphicsExtractor(net.minecraft.client.Minecraft.getInstance(), state, 0, 0), DeltaTracker.ZERO);
        var result = new ArrayList<GuiTextRenderState>(); state.forEachText(result::add);
        return result;
    }
    private static Object field(Object object, String name) {
        try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class Preview extends Screen {
        private final FpsWidget widget;
        Preview(FpsWidget widget) { super(Component.empty()); this.widget = widget; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float tick) {}
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) {
            graphics.fill(0, 0, width, height, 0xff202428);
            widget.render(graphics, DeltaTracker.ZERO);
        }
    }
}
