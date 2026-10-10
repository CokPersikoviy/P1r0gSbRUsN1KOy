package ru.wilyfox.client.visuals;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Matrix4f;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.VisualsConfig;
import ru.wilyfox.client.hud.menu.ColorPickerSettingsComponent;

public final class VisualsExtendedClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var old = context.computeOnClient(client -> ConfigManager.get().visuals);
        var oldClipboard = context.computeOnClient(client -> client.keyboardHandler.getClipboard());
        var oldCamera = context.computeOnClient(client -> client.options.getCameraType());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> { ConfigManager.get().visuals = new VisualsConfig(); ConfigManager.get().visuals.sanitize(); });
            context.waitTicks(5);
            for (String section : List.of("SCREEN", "CROSSHAIR", "HITBOXES", "BLOCK_OUTLINE", "HAND", "CAMERA", "WORLD", "TAB")) {
                context.setScreen(() -> previewScreen(section)); context.waitTicks(2); context.takeScreenshot("visuals-settings-" + section.toLowerCase(java.util.Locale.ROOT));
            }
            context.runOnClient(client -> client.gui.setScreen(null));
            context.runOnClient(client -> { verifyWorld(client); verifyHands(client); verifyCrosshair(client); verifyOutlines(client); verifyTab(client); });
            context.waitTicks(3);
            context.takeScreenshot("visuals-interactive-tab");
            context.runOnClient(client -> { client.options.keyPlayerList.setDown(false); client.gui.setScreen(null); verifyCamera(client); });
            // Exercise actual render paths (not only helper methods) with each line style enabled.
            for (var style : VisualsConfig.LineStyle.values()) {
                context.runOnClient(client -> {
                    var c = ConfigManager.get().visuals;
                    c.hitboxes.show = true; c.hitboxes.custom = true; c.hitboxes.self = true; c.hitboxes.style = style;
                    c.hitboxes.fill = true; c.hitboxes.eyeLine = true; c.hitboxes.lookArrow = true;
                    client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    c.blockOutline = true; c.blockStyle = style; c.blockFill = true; c.blockThroughWalls = true;
                    c.blockAnimation = 90; c.blockFade = 120; c.blockRainbow = true;
                });
                context.waitTicks(4);
                context.takeScreenshot("visuals-lines-" + style.name().toLowerCase(java.util.Locale.ROOT));
            }
            context.runOnClient(client -> {
                var c = ConfigManager.get().visuals; c.hitboxes.show = c.hitboxes.custom = false;
                c.camera.enabled = false; VisualsCamera.tick(client, false); client.options.setCameraType(CameraType.FIRST_PERSON);
                c.compactReload = true;
            });
            var reload = context.computeOnClient(client -> {
                var future = client.reloadResourcePacks();
                expect(client.gui.overlay() == null && CompactReload.pending() instanceof LoadingOverlay, "World reload still installed the blocking overlay");
                return future;
            });
            for (int i = 0; i < 100 && !reload.isDone(); i++) context.waitTicks(2);
            expect(reload.isDone() && !reload.isCompletedExceptionally(), "Detached reload never completed vanilla callbacks");
            context.waitTicks(15);
            context.runOnClient(client -> expect(CompactReload.pending() == null && client.gui.overlay() == null, "Completed reload leaked its overlay"));
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); client.options.keyPlayerList.setDown(false); client.keyboardHandler.setClipboard(oldClipboard);
                ConfigManager.get().visuals.camera.enabled = false; VisualsCamera.tick(client, false);
                ConfigManager.get().visuals = old; client.options.setCameraType(oldCamera); VisualBlockOutline.reset(); VisualsTab.tick(client);
            });
        }
    }
    private static void verifyWorld(Minecraft client) {
        var c = ConfigManager.get().visuals; var extractor = new LightmapRenderStateExtractor(client.gameRenderer, client);
        var state = new LightmapRenderState();
        c.brightnessEnabled = true; c.brightness = 200; c.skyLightEnabled = c.blockLightEnabled = true;
        c.skyLightColor = 0xFF123456; c.blockLightColor = 0xFFABCDEF;
        extractor.tick(); extractor.extract(state, 1);
        expect(state.brightness == 1 && state.nightVisionEffectIntensity == 1 && state.darknessEffectScale == 0, "Brightness mixin did not apply cave lighting");
        expect(close(state.skyLightColor.x(), 0x12 / 255f) && close(state.blockLightTint.z(), 0xEF / 255f), "Lightmap tint mixin did not apply selected RGB");
        c.brightness = 120;
        extractor.extract(state, 1);
        expect(state.needsUpdate && close(state.nightVisionEffectIntensity, .2f), "Paused lightmap ignored a settings change without a tick");
        c.brightnessEnabled = c.skyLightEnabled = c.blockLightEnabled = false;
        extractor.extract(state, 1);
        expect(state.needsUpdate && state.nightVisionEffectIntensity == 0, "Disabling lighting did not restore vanilla without a tick");
        c.cloudColorEnabled = true; c.cloudColor = 0x00123456;
        expect(client.gameRenderer.mainCamera().attributeProbe().getValue(net.minecraft.world.attribute.EnvironmentAttributes.CLOUD_COLOR, 1) == c.cloudColor, "Cloud transparency was discarded");
        c.timeEnabled = true; c.time = 18000;
        expect(Math.floorMod(client.level.getOverworldClockTime(), 24000L) == 18000, "Client clock mixin did not apply custom time");
        float serverRain = client.getSingleplayerServer().overworld().getRainLevel(1);
        c.weather = VisualsConfig.Weather.THUNDER;
        expect(client.level.getRainLevel(1) == 1 && client.level.getThunderLevel(1) == 1, "Client weather mixin not applied");
        expect(client.getSingleplayerServer().overworld().getRainLevel(1) == serverRain, "Visual weather changed the authoritative server");
        var fog = new net.minecraft.client.renderer.fog.FogData(); fog.environmentalStart = 4; fog.environmentalEnd = 20;
        c.fogMode = VisualsConfig.FogMode.CUSTOM; c.fogDistance = 3; Visuals.fog(fog, FogType.NONE);
        expect(fog.environmentalStart == 12 && fog.environmentalEnd == 60, "Fog multiplier did not scale distances");
        c.fluidFog = false; Visuals.fog(fog, FogType.WATER); expect(fog.environmentalEnd >= 1_000_000, "Fluid fog could not be disabled");
        c.weather = VisualsConfig.Weather.CLEAR; c.fogMode = VisualsConfig.FogMode.DEFAULT;
        c.brightnessEnabled = c.skyLightEnabled = c.blockLightEnabled = false; c.fluidFog = true;
        c.skyColorEnabled = true; c.skyColor = 0xFF789FD1; c.time = 6000;
        final int[] color = {0x23112233};
        var picker = new ColorPickerSettingsComponent("Alpha", () -> color[0], v -> color[0] = v).withAlpha();
        invoke(picker, "applyColor", new Class<?>[]{int.class, boolean.class}, 0x00123456, true);
        expect(color[0] == 0x00123456, "ARGB picker changed transparent alpha");
        invoke(picker, "applyHsvColor", new Class<?>[0]); expect(color[0] >>> 24 == 0, "Palette editing lost alpha");
    }
    private static void verifyHands(Minecraft client) {
        int oldFov = client.options.fov().get(); client.options.fov().set(150);
        expect(client.options.fov().get() == 150, "Extended world FOV slider rejects 150"); client.options.fov().set(oldFov);
        var c = ConfigManager.get().visuals.hand; c.enabled = true; c.mirror = false;
        c.main.x = 0.25; c.main.y = -0.2; c.main.scale = 0.5; c.off.x = -0.4; c.off.scale = 1.25;
        var pose = new PoseStack(); VisualsHand.apply(pose, InteractionHand.MAIN_HAND); VisualsHand.scale(pose, InteractionHand.MAIN_HAND);
        expect(close(pose.last().pose().m11(), 0.5f) && close(pose.last().pose().m31(), -0.2f), "Hand transform discarded scale or translation");
        var off = new PoseStack(); VisualsHand.apply(off, InteractionHand.OFF_HAND); VisualsHand.scale(off, InteractionHand.OFF_HAND);
        expect(close(off.last().pose().m11(), 1.25f), "Off-hand transform not independent");
        c.fovEnabled = true; c.fov = 105;
        float actual = (Float) invoke(client.gameRenderer.mainCamera(), "calculateHudFov", new Class<?>[]{float.class}, 1f);
        expect(close(actual, 105f), "Hand FOV mixin not applied");
        c.equipAnimation = false; c.swingSpeed = 2;
        int fast = (Integer) invoke(client.player, net.minecraft.world.entity.LivingEntity.class, "getCurrentSwingDuration", new Class<?>[0]);
        c.enabled = false;
        int normal = (Integer) invoke(client.player, net.minecraft.world.entity.LivingEntity.class, "getCurrentSwingDuration", new Class<?>[0]);
        expect(fast == Math.max(1, Math.round(normal / 2f)), "Local swing duration mixin did not apply speed");
        c.enabled = true; client.player.setItemInHand(InteractionHand.MAIN_HAND, Items.DIAMOND_SWORD.getDefaultInstance());
    }
    private static void verifyCrosshair(Minecraft client) {
        var c = ConfigManager.get().visuals.crosshair; c.enabled = true; c.dot = true;
        for (var style : VisualsConfig.CrosshairStyle.values()) for (boolean invert : new boolean[]{false, true}) {
            c.style = style; c.invert = invert; var state = new GuiRenderState();
            invoke(client.gui.hud, "extractCrosshair", new Class<?>[]{GuiGraphicsExtractor.class, DeltaTracker.class}, new GuiGraphicsExtractor(client, state, 0, 0), DeltaTracker.ZERO);
            int[] count = {0}; state.forEachElement(element -> count[0]++, GuiRenderState.TraverseRange.ALL);
            expect(count[0] > 0, "Crosshair style rendered no pixels: " + style + "/" + invert);
        }
        c.style = VisualsConfig.CrosshairStyle.CROSS; c.invert = false; c.dynamic = true;
        c.drawEnabled = true; c.indicator = VisualsConfig.Indicator.RING;
    }
    private static void verifyOutlines(Minecraft client) {
        var c = ConfigManager.get().visuals; c.blockOutline = true; c.blockFill = true;
        var state = new BlockOutlineRenderState(BlockPos.ZERO, false, true, Shapes.box(0, 0, 0, 1, 0.5, 1));
        var collector = (SimpleGizmoCollector) field(client.levelExtractor, "mainThreadGizmos");
        for (var style : VisualsConfig.LineStyle.values()) {
            collector.drainGizmos(); c.blockStyle = style;
            VisualBlockOutline.extract(state, true, client.gameRenderer.mainCamera());
            expect(!collector.drainGizmos().isEmpty(), "Advanced block outline submitted no geometry: " + style);
        }
        c.blockFade = 120; c.blockLinger = 100;
        VisualBlockOutline.extract(null, false, client.gameRenderer.mainCamera());
        expect(!collector.drainGizmos().isEmpty(), "Outline disappeared immediately despite linger");
        c.blockOutline = false; VisualBlockOutline.extract(state, true, client.gameRenderer.mainCamera());
        expect(collector.drainGizmos().isEmpty(), "Disabled outline retained geometry");
    }
    private static void verifyTab(Minecraft client) {
        var c = ConfigManager.get().visuals.tab; c.enabled = true; c.rows = 2; c.columns = 2;
        List<PlayerInfo> players = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            var info = new PlayerInfo(new GameProfile(new UUID(0, 100 + i), "VisualTest" + i), false);
            info.setTabListDisplayName(Component.literal("[125] VisualTest" + i)); players.add(info);
        }
        VisualsTab.tick(client);
        var first = VisualsTab.page(() -> players); expect(first.size() == 4, "TAB page ignored rows times columns");
        VisualsTab.turn(2); var last = VisualsTab.page(() -> { throw new AssertionError("TAB re-sorted its snapshot in the same frame"); });
        expect(last.size() == 2 && last.getFirst() == players.get(8), "Last TAB page clamped incorrectly");
        VisualsTab.tick(client);
        var listed = (Set<PlayerInfo>) field(client.getConnection(), "listedPlayers"); listed.addAll(players);
        client.options.keyPlayerList.setDown(true);
        expect(VisualsTab.press(true, 2) && VisualsTab.interactive(), "TAB cursor bind did not open screen");
        var state = new GuiRenderState(); client.gui.hud.getTabList().extractRenderState(new GuiGraphicsExtractor(client, state, 0, 0), client.getWindow().getGuiScaledWidth(), client.level.getScoreboard(), null);
        expect(VisualsTab.bottom() > 0, "Native TAB renderer did not register slot bounds");
        double x = client.mouseHandler.getScaledXPos(client.getWindow()), y = client.mouseHandler.getScaledYPos(client.getWindow());
        VisualsTab.page(() -> players); VisualsTab.slot((int)x - 1, (int)y - 1, (int)x + 10, (int)y + 10, 0);
        expect(VisualsTab.hovered() != null, "Interactive TAB did not map mouse to player");
        var screen = (VisualsTabScreen) client.gui.screen();
        screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false);
        String selected = (String) field(screen, "selected"); expect(selected != null && !selected.contains("[125]"), "TAB actions use decorated nickname");
        invoke(screen, "action", new Class<?>[]{int.class}, 3);
        expect(selected.equals(client.keyboardHandler.getClipboard()), "Copy nickname did not use real profile name");
    }
    private static void verifyCamera(Minecraft client) {
        var window = client.getWindow(); boolean focused = window.isFocused(); set(window, "focused", true);
        try {
            var c = ConfigManager.get().visuals.camera; c.enabled = true; c.mode = VisualsConfig.LookMode.HOLD;
            client.options.setCameraType(CameraType.FIRST_PERSON); float playerYaw = client.player.getYRot();
            VisualsCamera.tick(client, false); VisualsCamera.tick(client, true);
            expect(VisualsCamera.active() && client.options.getCameraType() == CameraType.THIRD_PERSON_BACK, "Hold freelook did not start");
            VisualsCamera.turn(100, 10000);
            expect(VisualsCamera.pitch() == 90 && client.player.getYRot() == playerYaw, "Freelook rotated the player or exceeded pitch limits");
            VisualsCamera.tick(client, false);
            expect(!VisualsCamera.active() && client.options.getCameraType() == CameraType.FIRST_PERSON, "Freelook did not restore previous perspective");
            c.mode = VisualsConfig.LookMode.TOGGLE; VisualsCamera.tick(client, true); VisualsCamera.tick(client, false);
            expect(VisualsCamera.active(), "Toggle freelook released on key-up");
            c.enabled = false; VisualsCamera.tick(client, false); expect(!VisualsCamera.active(), "Disabled freelook remained active");
        } finally { set(window, "focused", focused); }
    }
    private static net.minecraft.client.gui.screens.Screen previewScreen(String section) {
        try {
            Class<?> owner = Class.forName("ru.wilyfox.client.hud.menu.HudSettingsVisualsSection");
            Class<?> type = Class.forName("ru.wilyfox.client.hud.menu.HudSettingsVisualsSection$Section");
            var selected = owner.getDeclaredField("selected"); selected.setAccessible(true);
            selected.set(null, Enum.valueOf((Class)type, section));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        var panel = new ru.wilyfox.client.hud.menu.HudSettingsPanel(); set(panel, "activeCategory", ru.wilyfox.client.hud.menu.SettingsCategory.VISUALS);
        return new net.minecraft.client.gui.screens.Screen(Component.literal("Visuals preview test")) {
            @Override public boolean isPauseScreen() { return false; }
            @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) { panel.render(graphics, x, y); }
        };
    }
    private static Object field(Object object, String name) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void set(Object object, String name, Object value) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object invoke(Object object, String name, Class<?>[] types, Object... args) { return invoke(object, object.getClass(), name, types, args); }
    private static Object invoke(Object object, Class<?> type, String name, Class<?>[] types, Object... args) {
        try { var method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(object, args); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static boolean close(float a, float b) { return Math.abs(a - b) < 0.001f; }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
