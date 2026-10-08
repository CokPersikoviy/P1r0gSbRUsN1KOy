package ru.wilyfox.client.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import ru.wilyfox.client.hud.config.AspectRatioPreset;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.VisualsConfig;
import ru.wilyfox.client.hud.menu.HudSettingsPanel;
import ru.wilyfox.client.hud.menu.SettingsCategory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class VisualsClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var old = context.computeOnClient(client -> ConfigManager.get().visuals);
        var panel = new HudSettingsPanel();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.get().visuals = new VisualsConfig();
                setField(panel, "activeCategory", SettingsCategory.VISUALS);
            });
            context.setScreen(() -> new VisualsScreen(panel));
            context.waitTicks(3);
            context.takeScreenshot("visuals-mini-settings");
            context.runOnClient(client -> {
                var sections = (java.util.Map<?, ?>) field(panel, "componentsByCategory");
                expect(((List<?>) sections.get(SettingsCategory.VISUALS)).size() >= 13, "Visuals settings missing");
                client.gui.setScreen(null);
                var config = ConfigManager.get().visuals;
                var probe = client.gameRenderer.mainCamera().attributeProbe();
                int defaultSky = probe.getValue(EnvironmentAttributes.SKY_COLOR, 1);
                int defaultFog = probe.getValue(EnvironmentAttributes.FOG_COLOR, 1);
                config.skyColorEnabled = true; config.skyColor = 0xFF123456;
                expect(probe.getValue(EnvironmentAttributes.SKY_COLOR, 1) == 0x123456, "Sky mixin did not apply custom RGB");
                expect(probe.getValue(EnvironmentAttributes.FOG_COLOR, 1) == defaultFog, "Sky changed independent fog color");
                config.fogColorEnabled = true; config.fogColor = 0xFFABCDEF;
                expect(probe.getValue(EnvironmentAttributes.FOG_COLOR, 1) == 0xABCDEF, "Fog environment mixin did not apply RGB");
                config.skyColorEnabled = false;
                expect(probe.getValue(EnvironmentAttributes.SKY_COLOR, 1) == defaultSky, "Disabling sky did not restore server sky");
                var fogRenderer = (FogRenderer) field(client.gameRenderer, "fogRenderer");
                var fog = fogRenderer.setupFog(client.gameRenderer.mainCamera(), 8, DeltaTracker.ZERO, 0, client.level);
                expect(close(fog.color.x, 0xAB / 255f) && close(fog.color.y, 0xCD / 255f) && close(fog.color.z, 0xEF / 255f),
                        "Final fog renderer ignored selected color");
                var liquid = new FogData(); liquid.color = new Vector4f(0.2f, 0.3f, 0.4f, 1);
                liquid.environmentalStart = 4; liquid.environmentalEnd = 20;
                var before = new Vector4f(liquid.color);
                for (FogType type : new FogType[]{FogType.WATER, FogType.LAVA, FogType.POWDER_SNOW}) {
                    Visuals.fog(liquid, type);
                    expect(liquid.color.equals(before), "Custom color changed liquid/powder-snow fog");
                }
                expect(liquid.environmentalStart == 4 && liquid.environmentalEnd == 20, "Color setting changed fog distance");
                verifyOutline(client.levelRenderer);
                config.aspectRatio = AspectRatioPreset.R4_3;
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                checkProjection(client.gameRenderer.mainCamera(), 4f / 3);
                var config = ConfigManager.get().visuals;
                expect(close(Visuals.handWidth(1920, 1080), 1440), "Hand stretch does not match world ratio");
                config.stretchHand = false;
                expect(close(Visuals.handWidth(1920, 1080), 1920), "Hand stretch could not be disabled independently");
                config.aspectRatio = AspectRatioPreset.CUSTOM;
                config.aspectWidth = 1000; config.aspectHeight = 1000;
                config.skyColorEnabled = true; config.skyColor = 0xFFB288CC;
            });
            context.waitTicks(3);
            context.takeScreenshot("visuals-mini-custom-world");
            context.runOnClient(client -> {
                checkProjection(client.gameRenderer.mainCamera(), 1);
                ConfigManager.get().visuals.aspectRatio = AspectRatioPreset.OFF;
                ConfigManager.get().visuals.skyColorEnabled = false;
                ConfigManager.get().visuals.fogColorEnabled = false;
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                var window = client.getWindow();
                checkProjection(client.gameRenderer.mainCamera(), window.getScreenWidth() / (float) window.getScreenHeight());
            });
        } finally {
            context.runOnClient(client -> { ConfigManager.get().visuals = old; client.gui.setScreen(null); });
        }
    }

    private static void checkProjection(Object camera, float ratio) {
        var projection = (Projection) field(camera, "projection");
        Matrix4f matrix = projection.getMatrix(new Matrix4f());
        expect(close(matrix.m11() / matrix.m00(), ratio), "World projection does not match selected aspect ratio");
        Matrix4f culling = (Matrix4f) invoke(camera, "createProjectionMatrixForCulling", new Class<?>[0]);
        expect(close(culling.m11() / culling.m00(), ratio), "Frustum culling uses a different ratio");
    }

    private static void verifyOutline(Object renderer) {
        var config = ConfigManager.get().visuals;
        List<Object[]> calls = new ArrayList<>();
        var collector = (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, args) -> {
                    if (method.getName().equals("submitShapeOutline")) calls.add(args);
                    if (method.getName().equals("order")) return proxy;
                    return null;
                });
        var state = new LevelRenderState();
        state.cameraRenderState = new net.minecraft.client.renderer.state.level.CameraRenderState();
        state.cameraRenderState.pos = Vec3.ZERO;
        var shape = Shapes.box(0, 0, 0, 1, 0.5, 1);
        state.blockOutlineRenderState = new BlockOutlineRenderState(BlockPos.ZERO, false, true, shape);
        Class<?>[] types = {PoseStack.class, SubmitNodeCollector.class, LevelRenderState.class};
        config.blockOutline = false;
        invoke(renderer, "submitBlockOutline", types, new PoseStack(), collector, state);
        expect(calls.size() == 2, "Disabled override changed vanilla high-contrast passes");
        int vanillaColor = (Integer) calls.getLast()[3]; float vanillaWidth = (Float) calls.getLast()[4];
        config.blockOutline = true; config.blockOutlineColor = 0xFF43D5FF; config.blockOutlineWidth = 5;
        calls.clear(); invoke(renderer, "submitBlockOutline", types, new PoseStack(), collector, state);
        expect(calls.size() == 1, "Custom outline added an unnecessary contrast pass");
        expect(calls.getFirst()[1] == shape, "Custom outline lost exact slab shape");
        expect((Integer) calls.getFirst()[3] == config.blockOutlineColor && (Float) calls.getFirst()[4] == 5,
                "Actual outline submission ignored color or width");
        config.blockOutline = false;
        calls.clear(); invoke(renderer, "submitBlockOutline", types, new PoseStack(), collector, state);
        expect((Integer) calls.getLast()[3] == vanillaColor && (Float) calls.getLast()[4] == vanillaWidth,
                "Disabling outline did not restore vanilla rendering");
    }

    private static Object field(Object object, String name) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
    private static void setField(Object object, String name, Object value) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value); }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
    private static Object invoke(Object object, String name, Class<?>[] types, Object... args) {
        try { var method = object.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(object, args); }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
    private static boolean close(float first, float second) { return Math.abs(first - second) < 0.001f; }
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class VisualsScreen extends Screen {
        private final HudSettingsPanel panel;
        VisualsScreen(HudSettingsPanel panel) { super(Component.literal("Visuals mini test")); this.panel = panel; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) { panel.render(graphics, x, y); }
    }
}
