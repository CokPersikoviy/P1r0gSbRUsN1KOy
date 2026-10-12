package ru.wilyfox.client.hud;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.menu.HudSettingsPanel;
import ru.wilyfox.client.hud.widget.*;
import java.util.List;

/** Real pointer pipeline, persisted layout and input modifiers, rather than just geometry probes. */
public final class SnapEngineClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        int[] size = context.computeOnClient(client -> new int[]{client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight(), client.options.guiScale().get()});
        var renderer = new HudRenderer(new HudSettingsPanel());
        var parent = new Probe("BossHudWidget", 200, 40, 100, 200, "Tall neighbor");
        var child = new Probe("FpsWidget", 400, 120, 40, 20, "Drag me");
        var end = new Probe("BoostersWidget", 520, 220, 40, 20, "Child");
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.guiScale().set(2); client.getWindow().setWindowed(1280, 720); client.resizeGui();
                var config = ConfigManager.get();
                config.locationLayouts.clear(); config.widgetLayouts.clear(); config.widgetLocations.clear(); config.mainLayout.widgets.clear();
                config.mainLayout.widgets.addAll(List.of(parent.getConfigKey(), child.getConfigKey(), end.getConfigKey()));
                config.snapping = new HudSnapConfig(); config.snapping.screenTargets = false; config.render.nativeRenderer = true;
                ConfigManager.layoutChanged(); renderer.registerWidget(parent); renderer.registerWidget(child); renderer.registerWidget(end);
                client.gui.setScreen(new HudEditingScreen(renderer) {
                    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) {
                        renderer.render(graphics, DeltaTracker.ZERO);
                        renderer.renderSettingsOverlay(graphics, DeltaTracker.ZERO);
                    }
                });
                renderer.setEditing(true); renderer.onKeyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                drag(renderer, child, 308, 120, client, false);
                expect(child.getStartX() == 305 && child.getStartY() == 120, "Side snap moved the unrelated Y axis");
                expect(!child.hasWidgetSnap(), "Relation created before pointer release");
            });
            context.waitTicks(2);
            context.runOnClient(client -> expect(child.getStartX() == 305 && child.getStartY() == 120,
                    "Frame layout overwrote the live pointer's snap"));
            context.takeScreenshot("snap-engine-independent-axis-guide");
            context.getInput().holdControl();
            context.waitTicks(2);
            context.runOnClient(client -> expect(child.getStartX() == 308, "Control did not release a held guide without moving the pointer"));
            context.getInput().releaseControl();
            context.waitTicks(2);
            context.runOnClient(client -> {
                expect(child.getStartX() == 305, "Releasing Control did not restore live alignment");
                release(renderer, 308, 120, client);
                expect(parent.getConfigKey().equals(child.getSnapTargetKey()), "Release did not connect the side dock");
                expect(child.getSnapOffsetY() == 80, "Free motion along the neighboring side was not persisted");
                ConfigManager.layoutChanged(); renderer.refreshLayout();
                expect(child.getStartY() == 120 && child.getStartX() == 305, "Config reload lost the docking offset");
                renderer.onKeyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                expect(child.getStartX() == 400 && child.getStartY() == 120 && !child.hasWidgetSnap(), "Undo did not restore free placement");
                drag(renderer, child, 220, 120, client, false);
                expect(child.getStartX() == 305 && child.getStartY() == 120, "Ordinary drag placed a widget inside its neighbor");
                renderer.onMouseDragged(225, 145, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), 0);
                expect(child.getStartX() == 305 && child.getStartY() == 140, "Blocked side did not allow sliding along the neighbor");
                release(renderer, 220, 140, client);
                renderer.onKeyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                expect(child.getStartX() == 400 && child.getStartY() == 120, "Collision-limited drag did not undo as one action");
                ConfigManager.get().snapping.behavior = HudSnapConfig.Behavior.ALIGN_ONLY;
                drag(renderer, child, 308, 120, client, true);
                expect(child.getStartX() == 305 && !child.hasWidgetSnap() && child.getScreenAnchor() == null, "Align-only mode committed a relation");
            });
            context.getInput().holdControl();
            context.runOnClient(client -> {
                expect(ru.wilyfox.utils.InputModifiers.hasControlDown(), "Control not held");
                drag(renderer, child, 220, 120, client, true);
                expect(child.getStartX() == 220 && child.getStartY() == 120 && !child.hasWidgetSnap(), "Control did not allow intentional overlap");
            });
            context.getInput().releaseControl();
            context.runOnClient(client -> {
                ConfigManager.get().snapping.behavior = HudSnapConfig.Behavior.CONNECT;
                child.setStartX(400); child.setStartY(120); child.clearWidgetSnap();
                end.setStartX(449); end.setStartY(123);
                end.setWidgetSnap(child.getConfigKey(), WidgetCorner.TOP_LEFT, WidgetCorner.TOP_RIGHT, 4, 3);
                ConfigManager.saveWidgetLayouts(List.of(parent, child, end));
            });
            context.getInput().holdAlt();
            context.runOnClient(client -> {
                expect(ru.wilyfox.utils.InputModifiers.hasAltDown(), "Alt not held");
                drag(renderer, child, 220, 160, client, true);
                expect(child.getStartX() == 305 && end.getStartX() == 354 && end.getStartY() == 163,
                        "Group collision did not preserve member positions at the neighbor's side");
                renderer.onKeyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                expect(child.getStartX() == 400 && end.getStartX() == 449, "Group collision did not undo");
                int w = client.getWindow().getGuiScaledWidth();
                drag(renderer, child, w - 41, 290, client, true);
                expect(end.getRight() == w && end.getStartX() - child.getStartX() == 49, "Whole group was not clamped together");
                expect(!child.hasWidgetSnap() && end.getSnapTargetKey().equals(child.getConfigKey()), "Group snapped to its own member");
                expect(end.getSnapOffsetX() == 4 && end.getSnapOffsetY() == 3, "Group drag discarded connection offsets");
            });
            context.getInput().releaseAlt();
            context.runOnClient(client -> {
                renderer.onKeyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
                expect(child.getStartX() == 400 && end.getStartX() == 449, "Undo did not restore the whole group");
            });
            context.getInput().holdAlt();
            context.runOnClient(client -> {
                drag(renderer, child, 420, 170, client, false);
                renderer.selectLayout(""); // A layout/menu change can interrupt mouse release.
                expect(end.getSnapTargetKey().equals(child.getConfigKey()) && end.getSnapOffsetX() == 4,
                        "Interrupted group drag saved temporarily cleared connections");
            });
            context.getInput().releaseAlt();
            context.runOnClient(client -> {
                // Manual anchor offsets survive config application and resizing.
                child.clearWidgetSnap(); end.clearWidgetSnap();
                ConfigManager.captureWidgetLayout(end);
                child.setScreenAnchor(ru.wilyfox.client.hud.indicators.ScreenAnchor.BOTTOM_RIGHT); child.setAnchorOffsets(-12, -19);
                ConfigManager.captureWidgetLayout(child); ConfigManager.layoutChanged(); renderer.refreshLayout();
                expect(child.getStartX() == 580 && child.getStartY() == 313, "Anchor offsets not loaded: " + child.getStartX() + "," + child.getStartY());
                client.getWindow().setWindowed(1440, 900); client.resizeGui(); renderer.refreshLayout();
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                expect(child.getStartX() == 660 && child.getStartY() == 403, "Anchor offsets changed after resize");
                expect(child.getAnchorOffsetX() == -12 && child.getAnchorOffsetY() == -19, "Resize overwrote saved anchor offset");
                // Turn the neighbor into an inherited Main reference and connect a local child.
                var config = ConfigManager.get();
                var local = new LocationWidgetLayoutConfig(); local.inheritMainLayout = true; local.widgets.add(child.getConfigKey());
                config.locationLayouts.put("snap-local", local); ConfigManager.layoutChanged(); renderer.selectLayout("snap-local");
                child.setScreenAnchor(null); child.setStartX(400); child.setStartY(120);
                int px = parent.getStartX(), py = parent.getStartY();
                drag(renderer, child, parent.getRight() + 7, parent.getTop() + 80, client, true);
                expect(parent.getStartX() == px && parent.getStartY() == py, "Snap moved the inherited Main target");
                expect(!local.placements.containsKey(parent.getConfigKey()), "Snap wrote Main into the local layout");
                expect(child.hasWidgetSnap(), "Local child could not dock to inherited Main");
                System.out.println("Snap engine client: independent axes, collision barriers, sliding, Ctrl overlap, group collision/undo, release commit, offset reload, resize and inherited Main passed");
            });
        } finally {
            context.getInput().releaseControl(); context.getInput().releaseAlt();
            context.runOnClient(client -> {
                client.gui.setScreen(null); renderer.setEditing(false); ConfigManager.setEditorLayout(null); ConfigManager.restoreSnapshot(original);
                client.options.guiScale().set(size[2]); client.getWindow().setWindowed(size[0], size[1]); client.resizeGui();
            });
        }
    }
    private static void drag(HudRenderer renderer, Probe widget, int x, int y, net.minecraft.client.Minecraft client, boolean release) {
        renderer.onMousePressed(widget.getStartX() + 5, widget.getStartY() + 5, 0);
        renderer.onMouseDragged(x + 5, y + 5, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), 0);
        if (release) release(renderer, x, y, client);
    }
    private static void release(HudRenderer renderer, int x, int y, net.minecraft.client.Minecraft client) {
        renderer.onMouseReleased(0, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), x + 5, y + 5);
    }
    private static void expect(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private static final class Probe extends AbstractWidget {
        private final String label;
        Probe(String key, int x, int y, int w, int h, String label) { super(x, y, HudLayer.CONTENT); setConfigKey(key); width = w; height = h; this.label = label; }
        @Override public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
            HudSurface.fillRounded(graphics, getStartX(), getStartY(), getWidth(), getHeight(), 3, WidgetTheme.PANEL_BG);
            graphics.text(net.minecraft.client.Minecraft.getInstance().font, label, getStartX() + 3, getStartY() + 5, WidgetTheme.TEXT_PRIMARY);
        }
    }
}
