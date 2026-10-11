package ru.wilyfox.client.hud;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.internal.*;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.widget.*;
import java.util.*;

/** Exercise real GUI states, deferred glyph/items, and replayed departure snapshots. */
public final class WidgetOpacityClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                String key = WidgetCatalog.FPS.key();
                ConfigManager.get().mainLayout.widgets.add(key);
                ConfigManager.layoutChanged();
                var appearance = ConfigManager.getWidgetAppearance(key);
                appearance.opacityPercent = 50; appearance.backgroundOpacityPercent = 25;
                var probe = new ProbeWidget(key, 20, 30);
                var widgets = List.<Widget>of(probe);
                var animations = new WidgetLayoutAnimation(); animations.reset(widgets, widgets);
                long start = 1_000_000_000L;
                var state = new GuiRenderState();
                animations.render(probe, new GuiGraphicsExtractor(client, state, 0, 0), DeltaTracker.ZERO, start);
                check(state, .5f, .125f, true);
                // Changing layout must not restore full opacity or apply the previous frame's fade twice.
                animations.change(widgets, List.of(), start);
                var departure = new GuiRenderState();
                animations.renderDepartures(new GuiGraphicsExtractor(client, departure, 0, 0), start + 250_000_000L, null);
                check(departure, .25f, .0625f, true);
                animations.reset(widgets, widgets);
                appearance.backgroundOpacityPercent = 0;
                var noBackground = new GuiRenderState();
                animations.render(probe, new GuiGraphicsExtractor(client, noBackground, 0, 0), DeltaTracker.ZERO, start);
                check(noBackground, .5f, 0, false);
                appearance.opacityPercent = 100; appearance.backgroundOpacityPercent = 100;
                var full = new GuiRenderState();
                animations.render(probe, new GuiGraphicsExtractor(client, full, 0, 0), DeltaTracker.ZERO, start);
                check(full, 1, 1, true);
                // A nested chat idle fade should retain both independent multipliers in captured states.
                appearance.opacityPercent = 50; appearance.backgroundOpacityPercent = 25;
                List<Object> captured = new ArrayList<>(); Map<Object, Float> opacities = new IdentityHashMap<>();
                try (var scope = new WidgetRenderScope(captured, .8f, null, opacities);
                     var widget = WidgetRenderScope.appearance(probe);
                     var idle = WidgetRenderScope.fade(.5f)) {
                    var idleState = new GuiRenderState();
                    probe.render(new GuiGraphicsExtractor(client, idleState, 0, 0), DeltaTracker.ZERO);
                    check(idleState, .2f, .05f, true);
                }
                expect(opacities.values().stream().anyMatch(value -> value == .25f)
                        && opacities.values().stream().anyMatch(value -> value == .0625f), "Snapshot lost independent content/background factors");
                expect(WidgetRenderScope.alpha() == 1 && WidgetRenderScope.backgroundAlpha() == 1, "Opacity leaked into vanilla GUI");
                // Old config snapshots may have no appearance entry; controls must still read defaults after undo.
                var before = ConfigManager.snapshot();
                ConfigManager.getWidgetAppearance(key).opacityPercent = 35;
                ConfigManager.restoreSnapshot(before);
                expect(ConfigManager.getWidgetAppearance(key).opacityPercent == 50, "Undo did not restore widget opacity");
                client.gui.setScreen(new Screen(Component.literal("Per-widget opacity")) {
                    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
                        int[] opacity = {100, 50, 100, 50}, backgrounds = {100, 100, 0, 25};
                        String[] keys = {"FpsWidget", "BossHudWidget", "ActiveRunesWidget", "ScoreboardWidget"};
                        for (int i = 0; i < keys.length; i++) {
                            var config = ConfigManager.getWidgetAppearance(keys[i]);
                            config.opacityPercent = opacity[i]; config.backgroundOpacityPercent = backgrounds[i];
                            int x = 20 + i % 2 * 160, y = 35 + i / 2 * 90;
                            var widget = new ProbeWidget(keys[i], x, y);
                            try (var scope = WidgetRenderScope.appearance(widget)) { widget.render(graphics, DeltaTracker.ZERO); }
                            graphics.text(client.font, opacity[i] + "% / BG " + backgrounds[i] + "%", x, y + 67, -1);
                        }
                    }
                });
            });
            context.waitTicks(3);
            context.takeScreenshot("widget-independent-opacity");
        } finally {
            context.runOnClient(client -> { client.gui.setScreen(null); ConfigManager.restoreSnapshot(original); });
        }
    }

    private static void check(GuiRenderState state, float content, float background, boolean hasPanel) {
        int[] counts = new int[4];
        state.forEachElement(element -> {
            float alpha = element instanceof FadedGuiElement faded ? faded.alpha() : 1;
            var raw = element instanceof FadedGuiElement faded ? faded.original() : element;
            boolean marker = raw instanceof ColoredRectangleRenderState rectangle && rectangle.col1() == 0xff00ff00;
            expect(Math.abs(alpha - (marker ? content : background)) < .00001f, "Geometry opacity mismatch: " + alpha);
            counts[marker ? 0 : 1]++;
        }, GuiRenderState.TraverseRange.ALL);
        state.forEachText(text -> { counts[2]++; expect(Math.abs(((WidgetGuiState.Opacity) state).froghelper$opacity(text) - content) < .00001f, "Text opacity mismatch"); });
        state.forEachItem(item -> { counts[3]++; expect(Math.abs(((WidgetGuiState.Opacity) state).froghelper$opacity(item) - content) < .00001f, "Item opacity mismatch"); });
        expect(counts[0] == 1 && (counts[1] > 0) == hasPanel && counts[2] == 1 && counts[3] == 1, "Content missing or background not hidden");
    }
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class ProbeWidget extends AbstractWidget {
        ProbeWidget(String key, int x, int y) { super(x, y, HudLayer.CONTENT); setConfigKey(key); width = 145; height = 64; }
        @Override public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(startX, startY);
                HudSurface.drawPanel(graphics, 0, 0, width, height, WidgetChrome.SOLID, false);
                graphics.fill(5, 48, 120, 51, 0xff00ff00);
                graphics.text(net.minecraft.client.Minecraft.getInstance().font, "Widget text", 8, 8, -1);
                graphics.item(Items.DIAMOND_SWORD.getDefaultInstance(), 8, 25);
            } finally { graphics.pose().popMatrix(); }
        }
    }
}
