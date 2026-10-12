package ru.wilyfox.client.hud.menu;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.DarknessFogEnvironment;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.RenderConfig;
import ru.wilyfox.client.hud.config.VisualsConfig;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Test transformed vanilla sprint and fog code, rather than calling the mixin helpers. */
public final class GameplayUtilitiesClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.get().render = new RenderConfig();
                ConfigManager.get().visuals = new VisualsConfig();
                var sections = new EnumMap<SettingsCategory, List<SettingsComponent>>(SettingsCategory.class);
                for (var category : SettingsCategory.values()) sections.put(category, new ArrayList<>());
                HudSettingsCoreSections.populate(sections);
                for (String name : List.of("AutoSprint", "AntiBlind")) {
                    var toggle = sections.get(SettingsCategory.RENDER).stream()
                            .filter(item -> item.getLabel().equals(name)).findFirst().orElseThrow();
                    toggle.setSize(200, 20);
                    expect(toggle.mouseClicked(100, 10, 0), name + " did not toggle from Render settings");
                }
                var saved = ConfigManager.snapshot(); ConfigManager.restoreSnapshot(saved);
                expect(ConfigManager.get().render.autoSprint && ConfigManager.get().render.antiBlind,
                        "New toggles did not survive config reload");
                testSprint(client);
                testBlindness(client);
                System.out.println("Gameplay utilities: vanilla sprint restrictions, manual sprint, menus/focus, blindness fog/color and config reload passed");
            });
        } finally {
            context.runOnClient(client -> { client.gui.setScreen(null); ConfigManager.restoreSnapshot(original); });
        }
    }

    private static void testSprint(Minecraft client) {
        LocalPlayer player = client.player;
        ClientInput originalInput = player.input;
        boolean focused = client.getWindow().isFocused();
        var abilities = player.getAbilities();
        boolean flying = abilities.flying, mayfly = abilities.mayfly;
        try {
            set(client.getWindow(), "focused", true);
            abilities.flying = false; abilities.mayfly = false;
            player.getFoodData().setFoodLevel(20);
            player.removeEffect(MobEffects.BLINDNESS);
            ConfigManager.get().render.autoSprint = false;
            step(player, true, false, false);
            expect(!player.isSprinting(), "Disabled AutoSprint started running without the sprint key");
            step(player, true, false, true);
            expect(player.isSprinting(), "Disabled AutoSprint interfered with manual sprint");
            ConfigManager.get().render.autoSprint = true;
            step(player, true, false, false);
            expect(player.isSprinting(), "Forward input did not activate AutoSprint");
            expect(!player.input.keyPresses.sprint() && !client.options.keySprint.isDown(),
                    "AutoSprint left the sprint key pressed");
            step(player, false, false, false);
            expect(!player.isSprinting(), "AutoSprint started running without forward movement");
            step(player, true, true, false);
            expect(!player.isSprinting(), "AutoSprint bypassed crouching");
            player.getFoodData().setFoodLevel(6);
            step(player, true, false, false);
            expect(!player.isSprinting(), "AutoSprint bypassed hunger");
            player.getFoodData().setFoodLevel(20);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            player.startUsingItem(InteractionHand.MAIN_HAND);
            step(player, true, false, false);
            expect(!player.isSprinting(), "AutoSprint bypassed item use");
            player.stopUsingItem();
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 600));
            step(player, true, false, false);
            expect(!player.isSprinting(), "Visual AntiBlind changed blindness gameplay restrictions");
            player.removeEffect(MobEffects.BLINDNESS);
            client.gui.setScreen(new ChatScreen("", false));
            step(player, true, false, false);
            expect(!player.isSprinting(), "AutoSprint activated through an open menu");
            client.gui.setScreen(null);
            set(client.getWindow(), "focused", false);
            step(player, true, false, false);
            expect(!player.isSprinting(), "AutoSprint activated in an unfocused window");
            set(client.getWindow(), "focused", true);
            ConfigManager.get().render.autoSprint = false;
            step(player, true, false, false);
            expect(!player.isSprinting(), "Disabling AutoSprint kept requesting sprint");
        } finally {
            player.input = originalInput; player.setSprinting(false); player.stopUsingItem();
            abilities.flying = flying; abilities.mayfly = mayfly;
            player.removeEffect(MobEffects.BLINDNESS); client.gui.setScreen(null);
            set(client.getWindow(), "focused", focused);
        }
    }

    private static void step(LocalPlayer player, boolean forward, boolean shift, boolean sprint) {
        player.setSprinting(false); player.setOnGround(true);
        player.horizontalCollision = false; player.minorHorizontalCollision = false;
        set(player, "sprintTriggerTime", 0);
        player.input = new FixedInput(new Input(forward, false, false, false, false, shift, sprint));
        player.aiStep();
    }

    private static void testBlindness(Minecraft client) {
        var player = client.player;
        var renderer = (FogRenderer) field(client.gameRenderer, "fogRenderer");
        player.removeEffect(MobEffects.BLINDNESS); player.removeEffect(MobEffects.DARKNESS);
        ConfigManager.get().render.antiBlind = false;
        var normal = fog(client, renderer);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 600));
        var blind = fog(client, renderer);
        expect(blind.environmentalEnd <= 5.01f, "Vanilla blindness did not restrict fog");
        ConfigManager.get().render.antiBlind = true;
        var clear = fog(client, renderer);
        expect(close(clear.environmentalEnd, normal.environmentalEnd), "AntiBlind retained blindness fog");
        expect(clear.color.distance(normal.color) < .0001f, "AntiBlind retained blindness color darkening");
        expect(blind.color.distance(clear.color) > .01f, "Fog color test did not exercise visual darkening");
        expect(player.hasEffect(MobEffects.BLINDNESS), "AntiBlind removed the status effect");
        ConfigManager.get().render.antiBlind = false;
        expect(close(fog(client, renderer).environmentalEnd, blind.environmentalEnd), "Disabling AntiBlind did not restore fog immediately");
        ConfigManager.get().render.antiBlind = true;
        player.removeEffect(MobEffects.BLINDNESS);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 600));
        expect(new DarknessFogEnvironment().isApplicable(FogType.NONE, player), "AntiBlind suppressed Darkness");
        var darkness = fog(client, renderer);
        ConfigManager.get().render.antiBlind = false;
        var unchanged = fog(client, renderer);
        expect(close(darkness.environmentalEnd, unchanged.environmentalEnd) && darkness.color.distance(unchanged.color) < .0001f,
                "AntiBlind changed another effect's fog");
        player.removeEffect(MobEffects.DARKNESS);
    }
    private static FogData fog(Minecraft client, FogRenderer renderer) {
        return renderer.setupFog(client.gameRenderer.mainCamera(), 8, DeltaTracker.ZERO, 0, client.level);
    }
    private static boolean close(float a, float b) { return a == b || Math.abs(a - b) < .0001f; }
    private static Object field(Object target, String name) {
        try { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void set(Object target, String name, Object value) {
        try { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class FixedInput extends ClientInput {
        FixedInput(Input keys) { keyPresses = keys; moveVector = new Vec2(0, keys.forward() ? 1 : 0); }
        @Override public void tick() { }
    }
}
