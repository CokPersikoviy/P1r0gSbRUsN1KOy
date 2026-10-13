package ru.wilyfox.client.hud.widget;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.internal.*;
import java.util.*;

/** Compare the compact antialiased mesh with the legacy renderer, including UVs/fades/clipping. */
public final class HudVisualParityClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var snapshot = context.computeOnClient(client -> ConfigManager.snapshot());
        int scale = context.computeOnClient(client -> client.options.guiScale().get());
        int width = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int height = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        DynamicTexture[] texture = {null};
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.get().mainLayout.widgets.clear(); ConfigManager.get().locationLayouts.clear(); ConfigManager.layoutChanged();
                var pixels = new NativeImage(2, 64, false);
                for (int y = 0; y < 64; y++) for (int x = 0; x < 2; x++) pixels.setPixel(x, y, 0xFF000000 | y * 4 << 16 | (255 - y * 4) << 8 | 0x60);
                texture[0] = new DynamicTexture(() -> "HUD parity gradient", pixels);
                int cases = 0;
                for (int radius : new int[]{0, 1, 2, 4, 6, 8, 16, 65}) for (float alpha : new float[]{1, .65f, .333f, .1f, Float.NaN})
                    for (int kind = 0; kind < 3; kind++) {
                        var oldState = state(client, texture[0], true, radius, alpha, kind);
                        var newState = state(client, texture[0], false, radius, alpha, kind);
                        var oldVertices = new Vertices(); var newVertices = new Vertices();
                        oldState.buildVertices(oldVertices); newState.buildVertices(newVertices);
                        if (radius == 0) {
                            check(oldVertices.points.size() == newVertices.points.size(), "Rectangular panel vertex count changed");
                            for (int i = 0; i < oldVertices.points.size(); i++)
                                check(Arrays.equals(oldVertices.points.get(i), newVertices.points.get(i)), "Rectangular panel changed");
                        } else {
                            check(newVertices.points.size() % 4 == 0, "Incomplete panel quad");
                            if (radius >= 4) check(newVertices.points.size() < oldVertices.points.size() * .75, "Mesh did not reduce geometry");
                            int maxAlpha = 0;
                            for (var point : newVertices.points) {
                                float px = Float.intBitsToFloat(point[0]), py = Float.intBitsToFloat(point[1]);
                                check(Float.isFinite(px) && Float.isFinite(py), "Invalid transformed panel vertex");
                                maxAlpha = Math.max(maxAlpha, point[3] >>> 24);
                                if (kind == 1) {
                                    check(Math.abs(Float.intBitsToFloat(point[4]) - px / 640) < .00001, "Blur UV lost screen alignment");
                                    check(Math.abs(Float.intBitsToFloat(point[5]) - (1 - py / 480)) < .00001, "Blur V lost screen alignment");
                                }
                            }
                            if (kind != 2) check(maxAlpha == Math.round((kind == 0 ? 145 : 255) * alpha), "Opacity changed");
                        }
                        check(oldState.bounds().equals(newState.bounds()) && oldState.scissorArea().equals(newState.scissorArea()), "Panel clipping changed");
                        cases++;
                    }
                System.out.println("HUD mesh: " + cases + " cases preserve clipping/UV/opacity and reduce geometry");
                client.getWindow().setWindowed(1280, 960);
            });
            context.waitTicks(2);
            context.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); client.gui.setScreen(new ParityScreen(texture[0])); });
            context.waitTicks(3); context.takeScreenshot("hud-panel-visual-parity");
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); if (texture[0] != null) texture[0].close(); ConfigManager.restoreSnapshot(snapshot);
                client.getWindow().setWindowed(width, height); client.options.guiScale().set(scale); client.resizeGui();
            });
        }
    }
    private static GuiElementRenderState state(net.minecraft.client.Minecraft client, DynamicTexture texture, boolean legacy, int radius, float alpha, int kind) {
        var state = new GuiRenderState(); var g = new GuiGraphicsExtractor(client, state, 0, 0);
        g.pose().translate(14.25f, 7.5f); g.pose().rotate(.08f); g.pose().scale(1.2f, .8f); g.enableScissor(0, 0, 230, 170);
        try (var scope = new WidgetRenderScope(null, alpha)) { paint(g, texture, legacy, 0, 0, 180, 132, radius, kind); }
        List<GuiElementRenderState> list = new ArrayList<>(); state.forEachElement(list::add, GuiRenderState.TraverseRange.ALL);
        check(list.size() == 1, "Panel batching changed"); return list.getFirst();
    }
    private static void paint(GuiGraphicsExtractor g, DynamicTexture texture, boolean legacy, int x, int y, int w, int h, int radius, int kind) {
        if (kind == 0) {
            if (legacy) LegacyRoundedPanelRenderState.fill(g, x, y, w, h, radius, 0x914783BC);
            else RoundedPanelRenderState.fill(g, x, y, w, h, radius, 0x914783BC);
        } else if (kind == 1) {
            if (legacy) LegacyRoundedPanelRenderState.blit(g, texture.getTextureView(), texture.getSampler(), x, y, w, h, radius, 640, 480);
            else RoundedPanelRenderState.blit(g, texture.getTextureView(), texture.getSampler(), x, y, w, h, radius, 640, 480);
        } else {
            if (legacy) LegacyRoundedPanelRenderState.menuShade(g, x, y, w, h, radius, 480);
            else RoundedPanelRenderState.menuShade(g, x, y, w, h, radius, 480);
        }
    }
    private static final class ParityScreen extends Screen {
        private final DynamicTexture texture;
        ParityScreen(DynamicTexture texture) { super(Component.literal("HUD parity")); this.texture = texture; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            g.fill(0, 0, width, height, 0xFF18232B);
            g.text(font, "Previous", 20, 16, -1); g.text(font, "Optimized", 340, 16, -1);
            for (int kind = 0; kind < 3; kind++) for (int col = 0; col < 2; col++) {
                try (var scope = new WidgetRenderScope(null, kind == 2 ? .333f : .65f)) {
                    paint(g, texture, col == 0, 20 + col * 320, 40 + kind * 110, 180, 80, 8, kind);
                }
            }
        }
    }
    private static final class Vertices implements VertexConsumer {
        final List<int[]> points = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z) { points.add(new int[]{Float.floatToIntBits(x), Float.floatToIntBits(y), Float.floatToIntBits(z), 0, 0, 0}); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return setColor(a << 24 | r << 16 | g << 8 | b); }
        @Override public VertexConsumer setColor(int color) { points.getLast()[3] = color; return this; }
        @Override public VertexConsumer setUv(float u, float v) { points.getLast()[4] = Float.floatToIntBits(u); points.getLast()[5] = Float.floatToIntBits(v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
