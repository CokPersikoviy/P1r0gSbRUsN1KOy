package ru.wilyfox.client.statistic;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.visuals.VisualsCamera;
import ru.wilyfox.client.audio.UiSounds;

public final class MiningStatisticsTracker {
    public static final MiningStatisticsStore STORE = new MiningStatisticsStore();
    private static boolean registered, pauseDown, resetDown;
    private static java.util.UUID account;
    private MiningStatisticsTracker() {}
    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var next = client.getGameProfile().id();
            if (!next.equals(account)) { STORE.reset(); account = next; }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            STORE.suspend(ConfigManager.get().miningStatistics); pauseDown = resetDown = false;
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            var client = Minecraft.getInstance();
            if (client.player == null) return;
            try (var profile = ModProfiler.getInstance().scope("mining/statistics/message")) {
                if (overlay) STORE.actionBar(message.getString(), ConfigManager.get().miningStatistics);
                else STORE.message(message.getString(), ConfigManager.get().miningStatistics);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try (var profile = ModProfiler.getInstance().scope("tick/MiningStatistics")) {
                var config = ConfigManager.get().miningStatistics;
                if (client.player == null || client.level == null || client.isPaused()) { STORE.suspend(config); return; }
                STORE.advance(config);
                boolean allowed = client.gui.screen() == null && client.isWindowActive();
                boolean pause = allowed && VisualsCamera.down(client, config.pauseKey) && VisualsCamera.modifiers(config.pauseModifiers);
                boolean reset = allowed && VisualsCamera.down(client, config.resetKey) && VisualsCamera.modifiers(config.resetModifiers);
                if (pause && !pauseDown) togglePause();
                if (reset && !resetDown) reset();
                pauseDown = pause; resetDown = reset;
            }
        });
    }
    public static void recordBreak() {
        try (var profile = ModProfiler.getInstance().scope("mining/statistics/blockBreak")) {
            STORE.recordBreak(ConfigManager.get().miningStatistics);
        }
    }
    public static void togglePause() { STORE.togglePause(ConfigManager.get().miningStatistics); UiSounds.toggle(); }
    public static void reset() { STORE.reset(); UiSounds.play(ru.wilyfox.client.audio.UiSound.SUCCESS); }
}
