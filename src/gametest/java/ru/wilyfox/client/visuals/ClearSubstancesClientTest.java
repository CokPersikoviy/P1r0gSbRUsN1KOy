package ru.wilyfox.client.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.RenderConfig;
import ru.wilyfox.client.hud.config.VisualsConfig;
import java.lang.reflect.Proxy;

public final class ClearSubstancesClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.get().render = new RenderConfig();
                ConfigManager.get().visuals = new VisualsConfig();
                var camera = client.gameRenderer.mainCamera();
                var fogRenderer = (FogRenderer) field(client.gameRenderer, "fogRenderer");
                BlockPos center = BlockPos.containing(camera.position());
                var blockStates = new BlockState[]{Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState(), Blocks.POWDER_SNOW.defaultBlockState()};
                var types = new FogType[]{FogType.WATER, FogType.LAVA, FogType.POWDER_SNOW};
                for (int i = 0; i < types.length; i++) {
                    fill(client, center, blockStates[i]);
                    expect(camera.getFluidInCamera() == types[i], "Camera did not enter " + types[i]);
                    var normal = fogRenderer.setupFog(camera, 8, DeltaTracker.ZERO, 0, client.level);
                    expect(normal.environmentalEnd < 1_000_000, "Disabled setting changed " + types[i]);
                    // A switch for another substance must not clear the current one.
                    select((i + 1) % 3);
                    var independent = fogRenderer.setupFog(camera, 8, DeltaTracker.ZERO, 0, client.level);
                    expect(independent.environmentalEnd == normal.environmentalEnd, "Substance switches are not independent");
                    select(i);
                    var clear = fogRenderer.setupFog(camera, 8, DeltaTracker.ZERO, 0, client.level);
                    expect(clear.environmentalStart >= 1_000_000 && clear.environmentalEnd >= 1_000_000
                            && clear.renderDistanceStart >= 1_000_000 && clear.renderDistanceEnd >= 1_000_000
                            && clear.skyEnd >= 1_000_000 && clear.cloudEnd >= 1_000_000, "Fog was not completely cleared for " + types[i]);
                    expect(clear.color.equals(normal.color), "Clear setting changed configured fog color");
                    select(-1);
                    var restored = fogRenderer.setupFog(camera, 8, DeltaTracker.ZERO, 0, client.level);
                    expect(restored.environmentalEnd == normal.environmentalEnd, "Fog did not return immediately after disabling");
                }
                int[] submissions = {0};
                var collector = (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                        new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, args) -> {
                            if (method.getName().equals("submitCustomGeometry")) submissions[0]++;
                            return method.getName().equals("order") ? proxy : null;
                        });
                Class<?>[] waterSignature = {Minecraft.class, PoseStack.class, SubmitNodeCollector.class};
                invoke(null, ScreenEffectRenderer.class, "submitWater", waterSignature, client, new PoseStack(), collector);
                expect(submissions[0] == 1, "Vanilla water overlay did not submit geometry");
                ConfigManager.get().render.clearWater = true; submissions[0] = 0;
                invoke(null, ScreenEffectRenderer.class, "submitWater", waterSignature, client, new PoseStack(), collector);
                expect(submissions[0] == 0, "Clear Water retained the dark screen overlay");

                var screenEffects = field(client.gameRenderer, "screenEffectRenderer");
                var sprite = ((SpriteGetter) field(screenEffects, "sprites")).get(ModelBakery.FIRE_1);
                Class<?>[] fireSignature = {PoseStack.class, SubmitNodeCollector.class, TextureAtlasSprite.class};
                fill(client, center, Blocks.LAVA.defaultBlockState());
                ConfigManager.get().render.clearLava = true;
                invoke(null, ScreenEffectRenderer.class, "submitFire", fireSignature, new PoseStack(), collector, sprite);
                expect(submissions[0] == 0, "Clear Lava retained fire while submerged");
                fill(client, center, Blocks.AIR.defaultBlockState());
                invoke(null, ScreenEffectRenderer.class, "submitFire", fireSignature, new PoseStack(), collector, sprite);
                expect(submissions[0] == 1, "Clear Lava also hid unrelated fire on land");

                var frozen = (Identifier) field(client.gui.hud, "POWDER_SNOW_OUTLINE_LOCATION");
                Class<?>[] textureSignature = {GuiGraphicsExtractor.class, Identifier.class, float.class};
                var normalOverlay = new GuiRenderState();
                invoke(client.gui.hud, Hud.class, "extractTextureOverlay", textureSignature, new GuiGraphicsExtractor(client, normalOverlay, 0, 0), frozen, 1f);
                expect(count(normalOverlay) > 0, "Vanilla frozen overlay was empty");
                ConfigManager.get().render.clearPowderSnow = true;
                var clearedOverlay = new GuiRenderState();
                invoke(client.gui.hud, Hud.class, "extractTextureOverlay", textureSignature, new GuiGraphicsExtractor(client, clearedOverlay, 0, 0), frozen, 1f);
                expect(count(clearedOverlay) == 0, "Clear Powder Snow retained the frozen frame");
                var unrelatedOverlay = new GuiRenderState();
                invoke(client.gui.hud, Hud.class, "extractTextureOverlay", textureSignature, new GuiGraphicsExtractor(client, unrelatedOverlay, 0, 0), Identifier.withDefaultNamespace("misc/pumpkinblur"), 1f);
                expect(count(unrelatedOverlay) > 0, "Snow setting hid unrelated texture overlays");
                var air = fogRenderer.setupFog(camera, 8, DeltaTracker.ZERO, 0, client.level);
                expect(air.renderDistanceEnd < 1_000_000, "Clear substance settings also disabled ordinary air fog");
                System.out.println("Clear substances: actual camera fog, independent toggles, immediate restoration, water/fire/frozen overlays passed");
            });
        } finally {
            context.runOnClient(client -> ConfigManager.restoreSnapshot(original));
        }
    }
    private static void select(int selected) {
        var render = ConfigManager.get().render;
        render.clearWater = selected == 0; render.clearLava = selected == 1; render.clearPowderSnow = selected == 2;
    }
    private static void fill(Minecraft client, BlockPos center, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) client.level.setBlock(pos, state, 0);
    }
    private static int count(GuiRenderState state) {
        int[] count = {0}; state.forEachElement(element -> count[0]++, GuiRenderState.TraverseRange.ALL); return count[0];
    }
    private static Object field(Object object, String name) {
        try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void invoke(Object object, Class<?> owner, String name, Class<?>[] types, Object... args) {
        try { var method = owner.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(object, args); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
