package ru.wilyfox.client.visuals;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import ru.wilyfox.client.hud.config.VisualsConfig;

public final class CrosshairBatchClientTest implements FabricClientGameTest {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("froghelper", "textures/gui/visuals_white.png");
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                int cases = 0, oldCount = 0, newCount = 0;
                for (var style : VisualsConfig.CrosshairStyle.values()) for (boolean invert : new boolean[]{false, true})
                    for (boolean clipped : new boolean[]{false, true}) {
                        var shape = shape(style, invert);
                        var old = elements(client, shape, invert, clipped, false);
                        var fresh = elements(client, shape, invert, clipped, true);
                        check(quads(old).equals(quads(fresh)), "Crosshair vertices/UV/colour changed: " + style + "/" + invert + "/" + clipped);
                        check(fresh.size() <= 3, "Grouped shape still submitted per scanline");
                        for (var element : fresh) {
                            check(element.bounds() != null, "Grouped crosshair lost bounds");
                            check(clipped == (element.scissorArea() != null), "Grouped crosshair lost scissor");
                        }
                        cases++; oldCount += old.size(); newCount += fresh.size();
                    }
                System.out.println("FH crosshair: " + cases + " exact geometry/UV/colour/scissor cases; GUI elements=" + oldCount + " -> " + newCount);
            });
            context.setScreen(() -> grid(false)); context.waitTicks(3); context.takeScreenshot("crosshair-legacy");
            context.setScreen(() -> grid(true)); context.waitTicks(3); context.takeScreenshot("crosshair-batched");
        } finally { context.runOnClient(client -> client.gui.setScreen(null)); }
    }
    private static CrosshairGeometry.Shape shape(VisualsConfig.CrosshairStyle style, boolean invert) {
        return new CrosshairGeometry().shape(new CrosshairGeometry.Pixels(style, 11, 8, 3, 5, true, true, true, true, 5, invert ? 0 : 2));
    }
    private static List<GuiElementRenderState> elements(Minecraft client, CrosshairGeometry.Shape shape, boolean invert,
                                                        boolean clipped, boolean batch) {
        var state = new GuiRenderState(); var graphics = new GuiGraphicsExtractor(client, state, 0, 0);
        graphics.pose().translate(4.25f, 2.5f).scale(1.25f, .75f);
        if (clipped) graphics.enableScissor(10, 8, 80, 60);
        paint(graphics, shape, 40, 35, invert, batch);
        graphics.pose().identity(); // Published elements must own a snapshot.
        var elements = new ArrayList<GuiElementRenderState>();
        state.forEachElement(elements::add, GuiRenderState.TraverseRange.ALL);
        return elements;
    }
    private static void paint(GuiGraphicsExtractor g, CrosshairGeometry.Shape shape, int x, int y, boolean invert, boolean batch) {
        fill(g, shape.outline(), x, y, 0x75123456, false, batch);
        fill(g, shape.lines(), x, y, 0xCB12FF56, invert, batch);
        fill(g, shape.dot(), x, y, 0xFFFF3366, invert, batch);
    }
    private static void fill(GuiGraphicsExtractor g, List<CrosshairGeometry.Rect> rects, int x, int y, int color, boolean invert, boolean batch) {
        if (batch) { CrosshairRenderState.fill(g, x, y, rects, color, invert); return; }
        for (var r : rects) {
            if (invert) g.blit(RenderPipelines.CROSSHAIR, WHITE, x + r.x(), y + r.y(), 0f, 0f, r.width(), r.height(), r.width(), r.height(), -1);
            else g.fill(x + r.x(), y + r.y(), x + r.x() + r.width(), y + r.y() + r.height(), color);
        }
    }
    private static List<String> quads(List<GuiElementRenderState> elements) {
        var result = new ArrayList<String>();
        for (var element : elements) {
            var sink = new Sink(); element.buildVertices(sink);
            for (int i = 0; i < sink.vertices.size(); i += 4) {
                result.add(System.identityHashCode(element.pipeline()) + ":" + element.textureSetup() + ":"
                        + element.scissorArea() + ":" + sink.vertices.subList(i, i + 4));
            }
        }
        result.sort(String::compareTo); return result;
    }
    private static Screen grid(boolean batch) {
        return new Screen(Component.literal("Crosshair parity")) {
            @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float tick) {
                g.fill(0, 0, width, height, 0xFF203B57);
                for (int x = 0; x < width; x += 8) g.fill(x, 0, x + 4, height, 0xFFB9A878);
                for (var style : VisualsConfig.CrosshairStyle.values()) for (int row = 0; row < 2; row++) {
                    boolean invert = row == 1;
                    paint(g, shape(style, invert), 38 + style.ordinal() * 65, 45 + row * 65, invert, batch);
                }
                g.enableScissor(40, 140, 250, 185);
                g.pose().pushMatrix(); g.pose().translate(5.25f, 1.5f).scale(1.25f, .75f);
                paint(g, shape(VisualsConfig.CrosshairStyle.X, true), 40, 200, true, batch);
                paint(g, shape(VisualsConfig.CrosshairStyle.CIRCLE, false), 130, 200, false, batch);
                g.pose().popMatrix(); g.disableScissor();
            }
            @Override public boolean isPauseScreen() { return false; }
        };
    }
    private static final class Sink implements VertexConsumer {
        final List<List<Object>> vertices = new ArrayList<>();
        private List<Object> current;
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            current = new ArrayList<>(List.of(x, y, z, 0, 0f, 0f)); vertices.add(current); return this;
        }
        @Override public VertexConsumer setColor(int color) { current.set(3, color); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return setColor(a << 24 | r << 16 | g << 8 | b); }
        @Override public VertexConsumer setUv(float u, float v) { current.set(4, u); current.set(5, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
