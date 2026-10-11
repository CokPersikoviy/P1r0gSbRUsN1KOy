package ru.wilyfox.client.hud;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.*;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.internal.*;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.widget.*;
import ru.wilyfox.client.protocol.SocialProtocolFixture;
import java.util.List;

public final class InheritedLayoutClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        int[] size = context.computeOnClient(client -> new int[]{client.getWindow().getWidth(), client.getWindow().getHeight()});
        int guiScale = context.computeOnClient(client -> client.options.guiScale().get());
        HudRenderer[] editor = {null};
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.guiScale().set(2); client.getWindow().setWindowed(1280, 720); client.resizeGui();
                var config = ConfigManager.get();
                config.mainLayout.widgets.clear(); config.widgetLayouts.clear(); config.locationLayouts.clear();
                config.widgetAppearance.clear(); config.widgetLocations.clear();
                config.render.nativeRenderer = true;
                config.mainLayout.widgets.add("BossHudWidget");
                config.widgetLayouts.put("BossHudWidget", placement(300, 80));
                var layout = new LocationWidgetLayoutConfig(); layout.inheritMainLayout = true;
                layout.widgets.add("FpsWidget"); layout.placements.put("FpsWidget", placement(400, 140));
                config.locationLayouts.put("test-child", layout); ConfigManager.setEditorLayout(null);
                var renderer = new HudRenderer(null); editor[0] = renderer;
                var parent = new ProbeWidget("BossHudWidget", 300, 80, 0xff00ff00);
                var child = new ProbeWidget("FpsWidget", 400, 140, 0xff0000ff);
                renderer.registerWidget(parent); renderer.registerWidget(child);
                renderer.setEditing(true); renderer.selectLayout("test-child");
                expect(renderer.getLayoutWidgetCount() == 2 && renderer.getEditableWidgetCount() == 1, "Inherited widget missing or counted as an owned member");
                expect(renderer.isInheritedWidget(parent) && !renderer.isWidgetAdded(parent), "Main reference became an editable member");
                var state = new GuiRenderState();
                renderer.renderLayer(HudLayer.CONTENT, new GuiGraphicsExtractor(client, state, 0, 0), DeltaTracker.ZERO);
                int[] outlines = {0}, markers = {0};
                state.forEachElement(element -> {
                    var raw = element instanceof FadedGuiElement fade ? fade.original() : element;
                    float alpha = element instanceof FadedGuiElement fade ? fade.alpha() : 1;
                    if (raw instanceof DottedOutlineRenderState) outlines[0]++;
                    if (raw instanceof ColoredRectangleRenderState rect && rect.col1() == 0xff00ff00) {
                        expect(Math.abs(alpha - .65f) < .00001f, "Inherited content opacity mismatch"); markers[0]++;
                    }
                    if (raw instanceof RoundedPanelRenderState && alpha < 1)
                        expect(Math.abs(alpha - .65f * .35f) < .00001f, "Inherited background opacity mismatch");
                }, GuiRenderState.TraverseRange.ALL);
                expect(outlines[0] == 1 && markers[0] == 1, "Dotted outline missing or applied to an owned widget");
                expect(WidgetRenderScope.alpha() == 1 && WidgetRenderScope.backgroundAlpha() == 1, "Reference opacity leaked into the rest of the UI");
                client.gui.setScreen(new Screen(Component.literal("Inherited Main preview")) {
                    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
                        renderer.renderLayer(HudLayer.CONTENT, graphics, DeltaTracker.ZERO);
                        renderer.renderSettingsOverlay(graphics, DeltaTracker.ZERO);
                    }
                });
            });
            context.takeScreenshot("inherited-main-layout-editor");
            context.runOnClient(client -> {
                var renderer = editor[0];
                var parent = (ProbeWidget) renderer.getWidgets().get(0);
                var child = (ProbeWidget) renderer.getWidgets().get(1);
                int w = client.getWindow().getGuiScaledWidth(), h = client.getWindow().getGuiScaledHeight();
                renderer.onMousePressed(parent.getStartX() + 20, parent.getStartY() + 10, 0);
                renderer.onMouseDragged(370, 120, w, h, 0);
                renderer.onMouseReleased(0, w, h, 370, 120);
                expect(parent.getStartX() == 300 && parent.getStartY() == 80, "Dragging a reference modified Main");
                parent.visibleHere(false); // An empty Main preview must still be a snap target.
                renderer.onMousePressed(child.getStartX() + 20, child.getStartY() + 10, 0);
                double mx = parent.getRight() + 5 + 20, my = parent.getTop() + 10;
                renderer.onMouseDragged(mx, my, w, h, 0);
                renderer.onMouseReleased(0, w, h, mx, my);
                expect("BossHudWidget".equals(child.getSnapTargetKey()), "Empty inherited widget was not a snap target");
                var config = ConfigManager.get(); var layout = config.locationLayouts.get("test-child");
                expect("BossHudWidget".equals(layout.placements.get("FpsWidget").snapTarget), "Saving stripped an inherited snap target");
                expect(!layout.placements.containsKey("BossHudWidget") && config.widgetLayouts.get("BossHudWidget").x == 300,
                        "Saving the child captured an inherited placement");
                parent.visibleHere(true);
                expect(renderer.addWidget(parent), "Could not promote a Main reference to a local placement");
                expect(!renderer.isInheritedWidget(parent) && renderer.getLayoutWidgetCount() == 2, "Promotion duplicated a widget");
                parent.setScale(1.3f); parent.setStartX(330); ConfigManager.captureWidgetLayout(parent);
                expect(renderer.removeWidget(parent), "Could not remove a local override");
                expect(renderer.isInheritedWidget(parent) && parent.getStartX() == 300 && parent.getScale() == 1f,
                        "Removing an override did not restore the Main reference: inherited=" + renderer.isInheritedWidget(parent)
                                + " x=" + parent.getStartX() + " scale=" + parent.getScale());
                expect(!layout.widgets.contains("BossHudWidget") && config.widgetLayouts.get("BossHudWidget").scale == 1f,
                        "A local override was written into Main");
                ConfigManager.saveWidgetLayouts(List.of(parent, child));
                layout.inheritMainLayout = false; ConfigManager.layoutChanged(); renderer.refreshLayout();
                expect(renderer.getLayoutWidgetCount() == 1, "Turning inheritance off kept reference widgets");
                renderer.selectLayout("");
                expect(renderer.getLayoutWidgetCount() == 1 && !renderer.isInheritedWidget(parent) && parent.getStartX() == 300,
                        "Main editor retained reference styling or child placement");
                testIndependentOverrides(renderer, client, parent, child);
                System.out.println("Inherited layout: dotted outlines, opacity, immutable Main, empty previews, snap persistence and promotion passed");
            });
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                if (editor[0] != null) editor[0].setEditing(false);
                ConfigManager.setEditorLayout(null); ConfigManager.restoreSnapshot(original);
                client.options.guiScale().set(guiScale); client.getWindow().setWindowed(size[0], size[1]); client.resizeGui();
            });
        }
    }
    private static void testIndependentOverrides(HudRenderer renderer, net.minecraft.client.Minecraft client, ProbeWidget root, ProbeWidget middle) {
        var config = ConfigManager.get();
        var end = new ProbeWidget("BoostersWidget", 438, 80, 0xffff0000);
        config.mainLayout.widgets.add("FpsWidget"); config.mainLayout.widgets.add("BoostersWidget");
        config.widgetLayouts.put("BossHudWidget", placement(300, 80));
        config.widgetLayouts.put("FpsWidget", snappedPlacement(369, 80, "BossHudWidget"));
        config.widgetLayouts.put("BoostersWidget", snappedPlacement(438, 80, "FpsWidget"));
        var local = config.locationLayouts.get("test-child"); local.inheritMainLayout = true;
        local.widgets.clear(); local.placements.clear(); local.locationVisibility.add("bay");
        renderer.registerWidget(end); ConfigManager.layoutChanged(); renderer.refreshLayout();
        renderer.selectLayout("test-child");
        expect(renderer.addWidget(root), "Could not override Main's group root");
        drag(renderer, root, 300, 210, client);
        overlay(renderer, client);
        expect(root.getStartY() == 210 && middle.getStartX() == 369 && middle.getStartY() == 80
                && end.getStartX() == 438 && end.getStartY() == 80, "Moving a local root moved Main's group");
        root.setScale(2f); ConfigManager.captureWidgetLayout(root); overlay(renderer, client);
        expect(middle.getStartX() == 369 && end.getStartX() == 438 && root.getScale() == 2f,
                "Local scale changed Main's geometry or was reset while measuring it");
        root.setScale(1f); ConfigManager.captureWidgetLayout(root);
        renderer.selectLayout(""); renderer.selectLayout("test-child"); overlay(renderer, client);
        expect(root.getStartY() == 210 && middle.getStartY() == 80 && end.getStartY() == 80, "Reopening restored Main dependencies into a local root");
        expect(renderer.removeWidget(root) && renderer.addWidget(middle), "Could not override the middle of Main's chain");
        drag(renderer, middle, 360, 210, client); overlay(renderer, client);
        expect(root.getStartX() == 300 && root.getStartY() == 80 && middle.getStartY() == 210
                && end.getStartX() == 438 && end.getStartY() == 80, "Moving a middle override moved the tail of Main's chain");
        ConfigManager.restoreSnapshot(ConfigManager.snapshot()); renderer.refreshLayout(); overlay(renderer, client);
        expect(middle.getStartY() == 210 && end.getStartY() == 80, "Config reload moved the inherited tail");
        SocialProtocolFixture.location("PRISONEVO1", 1, "bay");
        renderer.setEditing(false); overlay(renderer, client);
        expect(middle.getStartY() == 210 && root.getStartY() == 80 && end.getStartX() == 438 && end.getStartY() == 80,
                "Gameplay changed the independent placements");
        ConfigManager.saveWidgetLayouts(renderer.getWidgets());
        expect("BossHudWidget".equals(ConfigManager.get().widgetLayouts.get("FpsWidget").snapTarget)
                && "FpsWidget".equals(ConfigManager.get().widgetLayouts.get("BoostersWidget").snapTarget), "Saving runtime references changed Main's graph");
        renderer.setEditing(true); renderer.selectLayout(""); overlay(renderer, client);
        expect(root.getStartY() == 80 && middle.getStartX() == 369 && middle.getStartY() == 80
                && end.getStartX() == 438 && end.getStartY() == 80 && "FpsWidget".equals(end.getSnapTargetKey()),
                "Returning to Main did not restore its original group");
        System.out.println("Independent overrides: root/middle drag, config reload, gameplay and original Main graph passed");
    }
    private static WidgetLayoutConfig snappedPlacement(int x, int y, String target) {
        var value = placement(x, y); value.snapTarget = target;
        value.snapOwnCorner = WidgetCorner.TOP_LEFT; value.snapTargetCorner = WidgetCorner.TOP_RIGHT; return value;
    }
    private static void drag(HudRenderer renderer, AbstractWidget widget, int x, int y, net.minecraft.client.Minecraft client) {
        int w = client.getWindow().getGuiScaledWidth(), h = client.getWindow().getGuiScaledHeight();
        renderer.onMousePressed(widget.getStartX() + 20, widget.getStartY() + 10, 0);
        renderer.onMouseDragged(x + 20, y + 10, w, h, 0);
        overlay(renderer, client);
        renderer.onMouseReleased(0, w, h, x + 20, y + 10);
    }
    private static void overlay(HudRenderer renderer, net.minecraft.client.Minecraft client) {
        renderer.renderLayer(HudLayer.CONTENT, new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO);
    }
    private static WidgetLayoutConfig placement(int x, int y) {
        var value = new WidgetLayoutConfig(); value.x = x; value.y = y; value.scale = 1f; return value;
    }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class ProbeWidget extends AbstractWidget {
        private final int color;
        ProbeWidget(String key, int x, int y, int color) { super(x, y, HudLayer.CONTENT); setConfigKey(key); width = 64; height = 32; this.color = color; }
        void visibleHere(boolean value) { visible = value; }
        @Override public int getWidth() { return Math.round(width * getScale()); }
        @Override public int getHeight() { return Math.round(height * getScale()); }
        @Override public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(startX, startY);
                HudSurface.drawPanel(graphics, 0, 0, width, height, WidgetChrome.SOLID, false);
                graphics.fill(5, 25, 58, 28, color);
                graphics.text(net.minecraft.client.Minecraft.getInstance().font, color == 0xff00ff00 ? "Main" : "Child", 7, 7, -1);
            } finally { graphics.pose().popMatrix(); }
        }
    }
}
