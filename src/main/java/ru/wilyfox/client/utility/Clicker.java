package ru.wilyfox.client.utility;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.keybinds.KeyBinds;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

public final class Clicker {
    private static boolean autoAttackEnabled = false;
    private static long lastAttackTime = 0L;

    private Clicker() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KeyBinds.AUTO_ATTACK.consumeClick()) {
                autoAttackEnabled = !autoAttackEnabled;
                info(LOGGER, "Auto {}: {}", getActionName(), autoAttackEnabled);
            }

            if (!autoAttackEnabled) {
                return;
            }

            if (client.player == null) {
                return;
            }

            long now = System.currentTimeMillis();

            int cps = ConfigManager.get().clicker.cps;
            long interval = Math.max(1L, 1000L / Math.max(1, cps));

            if (now - lastAttackTime >= interval) {
                lastAttackTime = now;
                click();
            }
        });
    }

    public static void click() {
        Minecraft minecraft = Minecraft.getInstance();
        KeyMapping.click(getActionKey(minecraft).getDefaultKey());
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
