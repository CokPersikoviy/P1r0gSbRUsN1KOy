package ru.wilyfox.client.utility;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.keybinds.KeyBinds;
import ru.wilyfox.client.profiler.ModProfiler;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

public final class Clicker {
    private static final ClickerActivation activation = new ClickerActivation();
    private static boolean autoAttackEnabled;
    private static long lastAttackTime = 0L;

    private Clicker() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Clicker::tick);
    }

    public static void tick(Minecraft client) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("tick/Clicker")) {
            int presses = 0;
            while (KeyBinds.AUTO_ATTACK.consumeClick()) presses++;
            var mode = ConfigManager.get().clicker.activation;
            boolean allowed = client.player != null && client.level != null && client.player.isAlive()
                    && client.gui.screen() == null && client.isWindowActive();
            if (client.player == null || client.level == null) activation.reset();
            boolean active = activation.update(mode, presses, KeyBinds.AUTO_ATTACK.isDown(), allowed);
            if (active != autoAttackEnabled && mode == ru.wilyfox.client.hud.config.ClickerConfig.Activation.TOGGLE && allowed) {
                ru.wilyfox.client.audio.UiSounds.toggle();
                info(LOGGER, "Auto {}: {}", getActionName(), active);
            }
            autoAttackEnabled = active;
            if (!active) {
                lastAttackTime = 0L;
                return;
            }

            long now = System.currentTimeMillis();

            int cps = ConfigManager.get().clicker.cps;
            long interval = Math.max(1L, 1000L / Math.max(1, cps));

            if (now - lastAttackTime >= interval) {
                lastAttackTime = now;
                click();
            }
        }
    }
    public static void click() {
        Minecraft minecraft = Minecraft.getInstance();
        KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.getKey(getActionKey(minecraft).saveString()));
    }

    private static KeyMapping getActionKey(Minecraft minecraft) {
        if (ConfigManager.get().clicker.useItem) {
            return minecraft.options.keyUse;
        }

        return minecraft.options.keyAttack;
    }

    private static String getActionName() {
        return ConfigManager.get().clicker.useItem ? "use" : "attack";
    }
}
