package ru.wilyfox.client.statistic;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.widget.MiningStatisticsWidget;
import ru.wilyfox.client.keybinds.KeyBinds;
import ru.wilyfox.client.utility.Clicker;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class MiningStatisticsClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        String oldAttack = context.computeOnClient(client -> client.options.keyAttack.saveString());
        String oldClicker = context.computeOnClient(client -> KeyBinds.AUTO_ATTACK.saveString());
        var renderer = Client.getInstance().getHudRenderer();
        var clock = new AtomicLong();
        var session = new MiningStatisticsStore(clock::get);
        var preview = new MiningStatisticsWidget(30, 40, HudLayer.CONTENT, session);
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                renderer.setSettings(false); renderer.setEditing(false);
                ConfigManager.get().miningStatistics = new MiningStatisticsConfig();
                ConfigManager.get().mainLayout.widgets.clear(); ConfigManager.get().locationLayouts.clear();
                ConfigManager.get().widgetLocations.clear(); ConfigManager.get().render.unclutterWidgets = false;
                ConfigManager.get().render.nativeRenderer = true;
                ConfigManager.layoutChanged(); renderer.refreshLayout();
                var registered = renderer.getWidgets().stream().filter(w -> w instanceof MiningStatisticsWidget).findFirst().orElseThrow();
                expect(!registered.isVisible(), "Mining widget was added without the user's choice");
                renderer.addWidget(registered); expect(registered.isVisible(), "Mining widget cannot be added to layouts");
                renderer.setEditing(true); expect(renderer.openWidgetSettings(registered), "Mining widget has no context menu");
                renderer.setEditing(false); renderer.setSettings(false);

                MiningStatisticsTracker.STORE.reset();
                message("+1,5M", true);
                var position = client.player.blockPosition().offset(2, 0, 0);
                client.level.setBlock(position, Blocks.STONE.defaultBlockState(), 0);
                expect(client.gameMode.destroyBlock(position), "Block fixture could not be destroyed");
                var result = MiningStatisticsTracker.STORE.snapshot(ConfigManager.get().miningStatistics);
                expect(result.blocks() == 1 && result.money() == 1_500_000, "Real block mixin or action bar did not reach the counter");
                message("Фиолетовая бочка дала 2B", false);
                message("Вы нашли шард! x5", false); message("Вы нашли шард! x5", false);
                result = MiningStatisticsTracker.STORE.snapshot(ConfigManager.get().miningStatistics);
                expect(result.money() == 2_001_500_000 && result.shards() == 10, "Server reward routing dropped income or collapsed real shard rewards");
                MiningStatisticsTracker.STORE.togglePause(ConfigManager.get().miningStatistics);
                message("Вы нашли шард! x5", false);
                expect(MiningStatisticsTracker.STORE.snapshot(ConfigManager.get().miningStatistics).shards() == 10,
                        "Incoming reward bypassed manual pause");

                var config = ConfigManager.get().miningStatistics; config.autoPause = false;
                session.actionBar("+1.2B", config);
                for (int i = 0; i < 1000; i++) session.recordBreak(config);
                session.message("Вы нашли шард! x128", config); clock.set(120_000_000_000L);
                session.snapshot(config);
                verifyTextBounds(client, preview);
                config.showTotals = false; config.showLabels = false; config.showIcons = false;
                verifyTextBounds(client, preview);
                config.showRates = false; verifyTextBounds(client, preview);
                int minimum = config.minimumWidth; String title = config.title;
                config.minimumWidth = 60; config.showTitle = true; config.title = "A long customized mining statistics title";
                verifyTextBounds(client, preview);
                config.showTitle = false; config.showBlocks = config.showMoney = config.showShards = false;
                verifyTextBounds(client, preview);
                config.minimumWidth = minimum; config.title = title;
                config.showBlocks = config.showMoney = config.showShards = true;
                config.showTotals = config.showRates = config.showLabels = config.showIcons = true;
                int width = preview.getWidth(), height = preview.getHeight(); preview.setScale(2);
                expect(preview.getWidth() == width * 2 && preview.getHeight() == height * 2, "Mining widget scale disagrees with its bounds");
                preview.setScale(1);
                verifyClicker(client);
                client.gui.setScreen(new Preview(preview));
            });
            for (var layout : MiningStatisticsConfig.Layout.values()) {
                context.runOnClient(client -> ConfigManager.get().miningStatistics.layout = layout);
                context.waitTicks(3); context.takeScreenshot("mining-statistics-" + layout.name().toLowerCase(java.util.Locale.ROOT));
                context.runOnClient(client -> verifyTextBounds(client, preview));
            }
            context.runOnClient(client -> {
                ConfigManager.get().miningStatistics.customColors = true;
                ConfigManager.get().miningStatistics.showMoney = false;
                ConfigManager.get().miningStatistics.showTitle = false;
                ConfigManager.get().render.lightweightHud = true;
                verifyTextBounds(client, preview);
                ConfigManager.save();
                var shared = SharedConfigCodec.pack(ConfigManager.snapshot(), new com.google.gson.JsonObject());
                expect(!shared.getAsJsonObject("settings").getAsJsonObject("miningStatistics").get("showMoney").getAsBoolean(),
                        "Mining settings were not preserved in shared profiles");
            });
            context.waitTicks(3); context.takeScreenshot("mining-statistics-custom-lightweight");
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); renderer.setEditing(false); renderer.setSettings(false);
                KeyBinds.AUTO_ATTACK.setDown(false);
                KeyBinds.AUTO_ATTACK.setKey(InputConstants.getKey(oldClicker));
                client.options.keyAttack.setKey(InputConstants.getKey(oldAttack)); KeyMapping.resetMapping();
                while (client.options.keyAttack.consumeClick()) {}
                MiningStatisticsTracker.STORE.reset(); ConfigManager.restoreSnapshot(original); renderer.refreshLayout();
            });
        }
    }
    private static void message(String text, boolean overlay) {
        ClientReceiveMessageEvents.GAME.invoker().onReceiveGameMessage(Component.literal(text), overlay);
    }
    private static void verifyClicker(Minecraft client) {
        client.gui.setScreen(null);
        var config = ConfigManager.get().clicker;
        config.activation = ClickerConfig.Activation.HOLD; config.useItem = false;
        client.options.keyAttack.setKey(InputConstants.getKey("key.keyboard.j"));
        KeyBinds.AUTO_ATTACK.setKey(InputConstants.getKey("key.keyboard.k")); KeyMapping.resetMapping();
        while (client.options.keyAttack.consumeClick()) {}
        KeyBinds.AUTO_ATTACK.setDown(true); Clicker.tick(client);
        expect(client.options.keyAttack.consumeClick(), "Holding clicker did not press the remapped attack key");
        KeyBinds.AUTO_ATTACK.setDown(false); Clicker.tick(client);
        expect(!client.options.keyAttack.consumeClick(), "Clicker continued after releasing the key");
        client.gui.setScreen(new Screen(Component.empty()) {});
        KeyBinds.AUTO_ATTACK.setDown(true); Clicker.tick(client);
        expect(!client.options.keyAttack.consumeClick(), "Holding the bind clicked through a menu");
        client.gui.setScreen(null); KeyBinds.AUTO_ATTACK.setDown(false); Clicker.tick(client);
        config.activation = ClickerConfig.Activation.TOGGLE;
        KeyMapping.click(InputConstants.getKey(KeyBinds.AUTO_ATTACK.saveString())); Clicker.tick(client);
        expect(client.options.keyAttack.consumeClick(), "Toggle mode stopped working");
        KeyMapping.click(InputConstants.getKey(KeyBinds.AUTO_ATTACK.saveString())); Clicker.tick(client);
        expect(!client.options.keyAttack.consumeClick(), "Second toggle did not stop the clicker");
    }
    private static void verifyTextBounds(Minecraft client, MiningStatisticsWidget widget) {
        var state = new GuiRenderState();
        widget.render(new GuiGraphicsExtractor(client, state, 0, 0), DeltaTracker.ZERO);
        var text = new ArrayList<GuiTextRenderState>(); state.forEachText(text::add);
        expect(!text.isEmpty(), "Mining widget rendered no text");
        int right = widget.getStartX() + widget.getWidth(), bottom = widget.getStartY() + widget.getHeight();
        for (var line : text) {
            var bounds = line.bounds();
            expect(bounds.right() <= right + 2 && bounds.bottom() <= bottom + 2,
                    "Mining text escaped its panel: " + bounds + " vs " + right + "," + bottom);
        }
    }
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class Preview extends Screen {
        private final MiningStatisticsWidget widget;
        Preview(MiningStatisticsWidget widget) { super(Component.empty()); this.widget = widget; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float tick) {}
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) {
            graphics.fill(0, 0, width, height, 0xff202428); widget.render(graphics, DeltaTracker.ZERO);
        }
    }
}
