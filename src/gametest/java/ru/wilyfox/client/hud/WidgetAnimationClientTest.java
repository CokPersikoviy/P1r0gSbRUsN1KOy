package ru.wilyfox.client.hud;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2f;
import ru.wilyfox.client.hud.internal.*;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.widget.*;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.menu.HudSettingsPanel;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.protocol.DwGameLocation;
import java.util.*;

public final class WidgetAnimationClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                long start = 1_000_000_000L;
                var widget = new ProbeWidget();
                var all = List.<Widget>of(widget);
                var animations = new WidgetLayoutAnimation();
                animations.reset(all, all);
                var first = new GuiRenderState();
                animations.render(widget, new GuiGraphicsExtractor(client, first, 0, 0), DeltaTracker.ZERO, start);
                expect(!elements(first).isEmpty(), "Initial widget snapshot is empty");
                widget.setStartX(110); widget.setStartY(220); widget.setScale(2);
                animations.change(all, all, start);
                var moved = new GuiRenderState();
                animations.render(widget, new GuiGraphicsExtractor(client, moved, 0, 0), DeltaTracker.ZERO, start + 100_000_000L);
                var rectangle = (ColoredRectangleRenderState) elements(moved).getFirst();
                expect(Math.abs(rectangle.pose().m20() - 60) < .01f && Math.abs(rectangle.pose().m21() - 120) < .01f,
                        "Render pose did not interpolate the destination");
                expect(widget.getStartX() == 110 && widget.getStartY() == 220 && widget.getScale() == 2,
                        "Animation overwrote the widget model");
                // Data disappearing on teleport must not prevent the last complete frame from fading out.
                animations.change(all, List.of(), start + 200_000_000L);
                widget.empty = true;
                var departing = new GuiRenderState();
                animations.renderDepartures(new GuiGraphicsExtractor(client, departing, 0, 0), start + 450_000_000L, null);
                expect(elements(departing).stream().allMatch(e -> e instanceof FadedGuiElement faded && faded.alpha() == .5f),
                        "Panel/custom geometry opacity differs from departure alpha");
                int[] counts = new int[2];
                departing.forEachText(text -> { counts[0]++; expect(((WidgetGuiState.Opacity) departing).froghelper$opacity(text) == .5f, "Text lost deferred opacity"); });
                departing.forEachItem(item -> { counts[1]++; expect(((WidgetGuiState.Opacity) departing).froghelper$opacity(item) == .5f, "Item lost deferred opacity"); });
                expect(counts[0] == 1 && counts[1] == 1, "Snapshot lost formatted text or item icons");
                expect(!animations.hasDepartures(start + 700_000_000L), "Departure snapshot outlived 500 ms");
                var expired = new GuiRenderState();
                animations.renderDepartures(new GuiGraphicsExtractor(client, expired, 0, 0), start + 700_000_000L, null);
                expect(elements(expired).isEmpty(), "Expired departure still renders");
                // Resource invalidation cancels references to the previous atlas/framebuffer.
                animations.reset(all, all); widget.empty = false;
                animations.render(widget, new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO, start);
                animations.change(all, List.of(), start);
                WidgetLayoutAnimation.invalidateResources(); animations.checkResources(all, List.of());
                expect(!animations.hasDepartures(start), "Reload retained stale texture references");
                animations.reset(all, all);
                animations.render(widget, new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO, start);
                animations.hiddenData(widget);
                animations.change(all, List.of(), start);
                expect(!animations.hasDepartures(start), "Normally expired content reappeared on teleport");
                // Scope cleanup, including exceptions, must leave vanilla GUI unaffected.
                try (var scope = new WidgetRenderScope(null, .25f)) { expect(WidgetRenderScope.alpha() == .25f, "Scope did not activate"); }
                expect(WidgetRenderScope.alpha() == 1, "Widget opacity leaked into vanilla GUI");
                var premultiplied = new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                        net.minecraft.client.gui.render.TextureSetup.noTexture(), new Matrix3x2f(), 0, 0, 16, 16, 0, 1, 0, 1, -1, null);
                expect(FadedGuiElement.apply(premultiplied, .5f).color() == 0x80808080, "Premultiplied icon fade changes brightness");
                exerciseLayoutSwitch(client);
                exerciseEditorSize(client);
            });
            // Exercise actual deferred glyph/item/oversized-item preparation and rendering, including Sodium.
            context.runOnClient(client -> client.gui.setScreen(new net.minecraft.client.gui.screens.Screen(Component.literal("Widget fades")) {
                @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                    try (var scope = new WidgetRenderScope(null, .5f)) {
                        var widget = new ProbeWidget(); widget.render(graphics, DeltaTracker.ZERO);
                        graphics.item(Items.TRIDENT.getDefaultInstance(), 90, 30);
                    }
                    graphics.text(client.font, "Vanilla opacity", 10, 100, -1);
                    // The snapshot transform also reaches deferred glyphs and both item pipelines.
                    var state = new org.joml.Matrix3x2f().translate(320, 50).scale(.5f).translate(-80, -50);
                    try (var scope = new WidgetRenderScope(null, 1, state)) {
                        var widget = new ProbeWidget(); widget.render(graphics, DeltaTracker.ZERO);
                        graphics.item(Items.TRIDENT.getDefaultInstance(), 90, 30);
                    }
                }
            }));
            context.waitTicks(3);
            context.takeScreenshot("widget-animation-opacity");
            context.runOnClient(client -> client.gui.setScreen(null));
        }
    }
    private static void exerciseEditorSize(net.minecraft.client.Minecraft client) {
        long start = 1_000_000_000L;
        var widget = new ProbeWidget();
        var all = List.<Widget>of(widget);
        var animations = new WidgetLayoutAnimation();
        animations.reset(all, List.of());
        animations.editorAdded(widget, start);
        var zero = new GuiRenderState();
        animations.renderEditor(widget, new GuiGraphicsExtractor(client, zero, 0, 0), start, () -> widget.render(new GuiGraphicsExtractor(client, zero, 0, 0), DeltaTracker.ZERO));
        expect(elements(zero).isEmpty(), "Added widget did not start at zero size");
        var half = new GuiRenderState(); var halfGraphics = new GuiGraphicsExtractor(client, half, 0, 0);
        animations.renderEditor(widget, halfGraphics, start + 100_000_000L, () -> widget.render(halfGraphics, DeltaTracker.ZERO));
        var rectangle = (ColoredRectangleRenderState) elements(half).getFirst();
        expect(rectangle.pose().m00() == .5f && rectangle.pose().m20() == 45 && rectangle.pose().m21() == 36.25f,
                "Widget does not grow around its center");
        expect(widget.getScale() == 1 && widget.getStartX() == 10 && widget.getStartY() == 20, "Growth changed configured placement or scale");
        var full = new GuiRenderState(); var fullGraphics = new GuiGraphicsExtractor(client, full, 0, 0);
        animations.renderEditor(widget, fullGraphics, start + 200_000_000L, () -> widget.render(fullGraphics, DeltaTracker.ZERO));
        expect(((ColoredRectangleRenderState) elements(full).getFirst()).pose().m00() == 1, "Growth did not finish after 200 ms");
        animations.editorRemoved(widget, start + 200_000_000L);
        widget.empty = true;
        var removed = new GuiRenderState();
        animations.renderDepartures(new GuiGraphicsExtractor(client, removed, 0, 0), start + 300_000_000L, null, true);
        expect(!elements(removed).isEmpty() && elements(removed).stream().allMatch(e -> e instanceof TransformedGuiElement transformed && transformed.transform().m00() == .5f),
                "Removed panel and custom geometry did not shrink together");
        int[] counts = new int[2];
        removed.forEachText(text -> { counts[0]++; expect(((WidgetGuiState.Opacity) removed).froghelper$transform(text).m00() == .5f, "Removed text did not inherit shrinking"); });
        removed.forEachItem(item -> { counts[1]++; expect(((WidgetGuiState.Opacity) removed).froghelper$transform(item).m00() == .5f, "Removed item did not inherit shrinking"); });
        expect(counts[0] == 1 && counts[1] == 1, "Shrink lost text/item snapshot after data removal");
        expect(!animations.hasDepartures(start + 400_000_000L, true), "Removed widget outlived 200 ms");
        removed.reset();
        expect(WidgetRenderScope.transform() == null && WidgetRenderScope.alpha() == 1, "Editor transform leaked into vanilla GUI");
    }
    private static void exerciseLayoutSwitch(net.minecraft.client.Minecraft client) {
        Object original = field(null, ConfigManager.class, "CONFIG");
        String originalEditor = (String) field(null, ConfigManager.class, "editorLayout");
        Object protocol = field(null, DiamondWorldProtocolClient.class, "STATE");
        Object originalLocation = field(protocol, protocol.getClass(), "currentGameLocation");
        var config = new HudConfig();
        config.mainLayout.widgets.add("BlocksPerSecondWidget");
        var fishing = new LocationWidgetLayoutConfig();
        fishing.locationVisibility.add("bay");
        fishing.widgets.addAll(List.of("BlocksPerSecondWidget", "EstimatedTpsWidget"));
        var position = new WidgetLayoutConfig(); position.x = 100; position.y = 60; position.scale = 1.2f;
        fishing.placements.put("BlocksPerSecondWidget", position);
        config.locationLayouts.put("fishing", fishing);
        var filter = new WidgetLocationConfig(); filter.locationHidden.add("mine");
        config.widgetLocations.put("BlocksPerSecondWidget", filter);
        try {
            write(null, ConfigManager.class, "CONFIG", config); ConfigManager.setEditorLayout(null); ConfigManager.layoutChanged();
            write(protocol, protocol.getClass(), "currentGameLocation", new DwGameLocation("market"));
            var renderer = new HudRenderer(new HudSettingsPanel());
            var blocks = new BlocksPerSecondWidget(10, 20, HudLayer.CONTENT);
            renderer.registerWidget(blocks);
            var tps = new EstimatedTpsWidget(20, 90, HudLayer.CONTENT);
            renderer.registerWidget(tps);
            var first = new GuiRenderState();
            renderer.renderLayer(HudLayer.CONTENT, new GuiGraphicsExtractor(client, first, 0, 0), DeltaTracker.ZERO);
            String saved = HudConfigCodec.createGson().toJson(config);
            write(protocol, protocol.getClass(), "currentGameLocation", new DwGameLocation("bay"));
            var switched = new GuiRenderState();
            renderer.renderLayer(HudLayer.CONTENT, new GuiGraphicsExtractor(client, switched, 0, 0), DeltaTracker.ZERO);
            expect(blocks.getStartX() == 100 && blocks.getStartY() == 60 && blocks.getScale() == 1.2f,
                    "Location layout destination was delayed");
            boolean[] moving = {false}, appearing = {false};
            switched.forEachText(text -> {
                float alpha = ((WidgetGuiState.Opacity) switched).froghelper$opacity(text);
                if (alpha == 1 && text.pose.m20() < 50) moving[0] = true;
                if (alpha < .1f) appearing[0] = true;
            });
            expect(moving[0] && appearing[0], "Runtime location change did not start motion and fade in");
            expect(saved.equals(HudConfigCodec.createGson().toJson(config)), "Runtime animation changed saved placements");
            write(protocol, protocol.getClass(), "currentGameLocation", new DwGameLocation("mine"));
            var hidden = new GuiRenderState();
            renderer.renderLayer(HudLayer.CONTENT, new GuiGraphicsExtractor(client, hidden, 0, 0), DeltaTracker.ZERO);
            var animations = (WidgetLayoutAnimation) field(renderer, HudRenderer.class, "layoutAnimation");
            expect(animations.hasDepartures(System.nanoTime()), "Per-widget location filter bypassed fade out");
            renderer.setEditing(true);
            expect(!animations.hasDepartures(System.nanoTime()), "Editor retained gameplay animation");
            var editor = new GuiRenderState();
            renderer.renderLayer(HudLayer.CONTENT, new GuiGraphicsExtractor(client, editor, 0, 0), DeltaTracker.ZERO);
            editor.forEachText(text -> expect(((WidgetGuiState.Opacity) editor).froghelper$opacity(text) == 1 && text.pose.m20() == 10,
                    "Main editor did not render exact placement and opacity"));
            expect(renderer.addWidget(tps) && renderer.isWidgetAdded(tps), "Editor add did not change membership immediately");
            var adding = new GuiRenderState();
            renderer.renderLayer(HudLayer.CONTENT, new GuiGraphicsExtractor(client, adding, 0, 0), DeltaTracker.ZERO);
            boolean[] growing = {false};
            adding.forEachText(text -> { if (text.pose.m00() < 1) growing[0] = true; });
            expect(growing[0], "Editor add did not start growth");
            expect(renderer.removeWidget(blocks) && !renderer.isWidgetAdded(blocks), "Editor remove did not update membership immediately");
            var removing = new GuiRenderState();
            renderer.renderLayer(HudLayer.CONTENT, new GuiGraphicsExtractor(client, removing, 0, 0), DeltaTracker.ZERO);
            boolean[] shrinking = {false};
            removing.forEachText(text -> { if (((WidgetGuiState.Opacity) removing).froghelper$transform(text) != null) shrinking[0] = true; });
            expect(shrinking[0] && animations.hasDepartures(System.nanoTime(), true), "Editor remove did not retain shrinking snapshot");
        } finally {
            write(null, ConfigManager.class, "CONFIG", original);
            write(protocol, protocol.getClass(), "currentGameLocation", originalLocation);
            ConfigManager.setEditorLayout(originalEditor); ConfigManager.layoutChanged();
            ConfigManager.save();
        }
    }
    private static Object field(Object object, Class<?> type, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void write(Object object, Class<?> type, String name, Object value) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static List<GuiElementRenderState> elements(GuiRenderState state) {
        var result = new ArrayList<GuiElementRenderState>();
        state.forEachElement(result::add, GuiRenderState.TraverseRange.ALL); return result;
    }
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class ProbeWidget extends AbstractWidget {
        boolean empty;
        ProbeWidget() { super(10, 20, HudLayer.CONTENT); width = 140; height = 65; }
        @Override public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
            if (empty) return;
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(startX, startY); graphics.pose().scale(scale, scale);
                graphics.fill(0, 0, 140, 40, 0xff334455);
                RoundedPanelRenderState.fill(graphics, 0, 45, 140, 20, 4, 0xff884466);
                graphics.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Formatted widget").withStyle(ChatFormatting.GREEN), 5, 5, -1, true);
                graphics.item(Items.DIAMOND_SWORD.getDefaultInstance(), 5, 20);
            } finally { graphics.pose().popMatrix(); }
        }
    }
}
