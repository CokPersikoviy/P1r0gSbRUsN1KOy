package ru.wilyfox;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import ru.wilyfox.client.dungeon.DungeonMapRenderer;
import ru.wilyfox.client.protocol.DwDungeonPosition;

import java.util.ArrayList;
import java.util.List;

public final class DungeonMapClientTest implements FabricClientGameTest {
    private static final float WIDGET_SCALE = 1.17f;
    private static final DwDungeonPosition POSITION = new DwDungeonPosition(37, 91);

    @Override
    public void runTest(ClientGameTestContext context) {
        int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
        int oldWidth = context.computeOnClient(client -> client.getWindow().getScreenWidth());
        int oldHeight = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        try (var world = context.worldBuilder().create()) {
            Identifier texture = context.computeOnClient(client -> {
                var data = MapItemSavedData.createForClient((byte) 0, false, client.level.dimension());
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        // Different quadrants and a cross at the server position make rotation visible.
                        data.setColor(x, y, (byte) ((x == POSITION.x() || y == POSITION.y()) ? 34 * 4 + 2
                                : (x < 64 ? (y < 64 ? 4 : 7) : (y < 64 ? 13 : 12)) * 4 + 2));
                    }
                }
                MapId id = new MapId(90_000_001);
                client.level.overrideMapData(id, data);
                return client.getMapTextureManager().prepareMapTexture(id, data);
            });
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.getWindow().setWindowed(1101, 751);
                client.resizeGui();
                verifyExtraction(client, texture, POSITION, true, true, 275, 90f);
                verifyExtraction(client, texture, new DwDungeonPosition(80, 64), false, true, 200, 90f);
                verifyExtraction(client, texture, POSITION, true, false, 125, 0f);
                verifyExtraction(client, texture, null, true, true, 310, 90f);
                verifyExtraction(client, texture, new DwDungeonPosition(-1, -1), true, true, 310, 90f);
            });
            context.setScreen(() -> new MapScreen(texture));
            context.waitTicks(5);
            context.takeScreenshot("dungeon-map-transforms-fractional-scale");
            context.setScreen(() -> null);
        } finally {
            context.runOnClient(client -> {
                client.options.guiScale().set(oldScale);
                client.getWindow().setWindowed(oldWidth, oldHeight);
                client.resizeGui();
            });
        }
    }

    private static void verifyExtraction(net.minecraft.client.Minecraft client, Identifier texture,
                                         DwDungeonPosition position, boolean anchor, boolean rotate,
                                         int zoomPercent, float yaw) {
        var state = new GuiRenderState();
        var graphics = new GuiGraphicsExtractor(client, state, 0, 0);
        graphics.pose().translate(19, 23);
        graphics.pose().scale(WIDGET_SCALE, WIDGET_SCALE);
        Matrix3x2f originalPose = new Matrix3x2f(graphics.pose());
        ScreenRectangle expectedClip = new ScreenRectangle(2, 2, 128, 128).transformAxisAligned(originalPose);
        DungeonMapRenderer.render(graphics, texture, 2, 2, position, anchor, rotate, zoomPercent, yaw);
        List<GuiElementRenderState> elements = new ArrayList<>();
        state.forEachElement(elements::add, GuiRenderState.TraverseRange.ALL);
        var map = elements.stream().filter(BlitRenderState.class::isInstance).map(BlitRenderState.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("Synthetic map must submit a real texture"));
        expect(map.textureSetup().texure0() != null, "Map texture must be prepared");
        expect(map.u0() >= 0 && map.v0() >= 0 && map.u1() <= 1 && map.v1() <= 1, "Map UV must stay in range");
        for (var element : elements) {
            expect(expectedClip.equals(element.scissorArea()), "Map and marker must share the scaled viewport scissor");
            expect(element.bounds() != null && expectedClip.encompasses(element.bounds()), "Zoomed/rotated bounds must be clipped");
        }
        boolean available = position != null && !(position.x() == -1 && position.y() == -1);
        var marker = elements.stream().filter(ColoredRectangleRenderState.class::isInstance)
                .map(ColoredRectangleRenderState.class::cast).filter(fill -> fill.col1() == 0xFFFFFFFF).findFirst();
        expect(marker.isPresent() == available, "Unavailable position must preserve a static map without a false player marker");
        if (available) {
            float localX = (position.x() - 1f) * 128f / 126f;
            float localY = (position.y() - 1f) * 128f / 126f;
            var projected = map.pose().transformPosition(localX, localY, new Vector2f());
            var markerPoint = marker.orElseThrow().pose().transformPosition(0, 0, new Vector2f());
            near(projected.x, markerPoint.x, "Marker must match the server pixel on the rendered map");
            near(projected.y, markerPoint.y, "Marker must match the server pixel on the rendered map");
            if (anchor) {
                near(19 + 66 * WIDGET_SCALE, markerPoint.x, "Anchoring must center raw X without dividing coordinates by two");
                near(23 + 66 * WIDGET_SCALE, markerPoint.y, "Anchoring must center raw Y");
            }
            near(WIDGET_SCALE, (float) Math.hypot(marker.orElseThrow().pose().m00(), marker.orElseThrow().pose().m01()),
                    "Player arrow size must stay constant when zoom changes");
            if (rotate) {
                near(WIDGET_SCALE, marker.orElseThrow().pose().m00(), "Rotating map must keep the player arrow pointing up");
                near(0f, marker.orElseThrow().pose().m01(), "Arrow heading must cancel map rotation");
            }
            float angle = rotate ? (float) Math.toRadians(180f - yaw) : 0f;
            float mapScale = WIDGET_SCALE * zoomPercent / 100f;
            near(mapScale * (float) Math.cos(angle), map.pose().m00(), "Texture must follow the configured zoom and heading");
            near(mapScale * (float) Math.sin(angle), map.pose().m01(), "Texture must follow the configured zoom and heading");
        } else {
            near(WIDGET_SCALE, map.pose().m00(), "Missing position must preserve the static fallback scale");
            near(0, map.pose().m01(), "Missing position must preserve the static fallback orientation");
        }
        expect(originalPose.equals(graphics.pose()), "Map rendering must restore its caller's matrix");
        graphics.fill(135, 135, 136, 136, 0xFF00FF00);
        elements.clear();
        state.forEachElement(elements::add, GuiRenderState.TraverseRange.ALL);
        var laterFill = elements.stream().filter(ColoredRectangleRenderState.class::isInstance)
                .map(ColoredRectangleRenderState.class::cast).filter(fill -> fill.col1() == 0xFF00FF00)
                .findFirst().orElseThrow(() -> new AssertionError("A later widget must remain drawable"));
        expect(laterFill.scissorArea() == null, "Map clipping must not leak into later widgets");
    }

    private static void near(float expected, float actual, String message) {
        expect(Math.abs(expected - actual) < 0.001f, message + " (" + expected + " != " + actual + ")");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class MapScreen extends Screen {
        private final Identifier texture;
        private MapScreen(Identifier texture) {
            super(Component.literal("Dungeon map regression"));
            this.texture = texture;
        }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.text(minecraft.font, "Anchored / rotating / 275%", 18, 5, 0xFFFFFFFF);
            graphics.text(minecraft.font, "Fixed map / heading arrow", 215, 5, 0xFFFFFFFF);
            graphics.text(minecraft.font, "Unanchored / rotating", 18, 201, 0xFFFFFFFF);
            graphics.text(minecraft.font, "No position: static map", 215, 201, 0xFFFFFFFF);
            draw(graphics, 18, 20, POSITION, true, true, 275, 90);
            draw(graphics, 215, 20, POSITION, false, false, 100, 0);
            draw(graphics, 18, 215, new DwDungeonPosition(80, 64), false, true, 200, 90);
            draw(graphics, 215, 215, null, true, true, 310, 90);
        }
        private void draw(GuiGraphicsExtractor graphics, int x, int y, DwDungeonPosition position,
                          boolean anchor, boolean rotate, int zoom, float yaw) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(WIDGET_SCALE, WIDGET_SCALE);
            graphics.fill(0, 0, 132, 132, 0xFF263446);
            DungeonMapRenderer.render(graphics, texture, 2, 2, position, anchor, rotate, zoom, yaw);
            graphics.pose().popMatrix();
        }
    }
}
