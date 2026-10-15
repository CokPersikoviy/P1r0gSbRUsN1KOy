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
    private static KeyMapping heldKey;

    private Clicker() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Clicker::tick);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            releaseHeldKey(client); activation.reset(); autoAttackEnabled = false; lastAttackTime = 0;
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(Clicker::releaseHeldKey);
    }

    public static void tick(Minecraft client) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("tick/Clicker")) {
            int presses = 0;
            while (KeyBinds.AUTO_ATTACK.consumeClick()) presses++;
            var mode = ConfigManager.get().clicker.activation;
            boolean allowed = client.player != null && client.level != null && client.player.isAlive()
                    && client.gui.screen() == null && !client.isPaused()
                    && (client.isWindowActive() || !client.options.pauseOnLostFocus);
            boolean switchMode = false;
            while (KeyBinds.CLICKER_MODE.consumeClick()) switchMode = !switchMode;
            if (switchMode && allowed && client.isWindowActive()) {
                releaseHeldKey(client);
                var config = ConfigManager.get().clicker;
                config.actionMode = config.actionMode == ru.wilyfox.client.hud.config.ClickerConfig.ActionMode.HOLD
                        ? ru.wilyfox.client.hud.config.ClickerConfig.ActionMode.CLICK
                        : ru.wilyfox.client.hud.config.ClickerConfig.ActionMode.HOLD;
                lastAttackTime = 0L;
                ConfigManager.save();
                client.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.translatable(
                        "froghelper.clicker.mode_changed", config.actionMode.name()), false);
                ru.wilyfox.client.audio.UiSounds.toggle();
            }
            if (client.player == null || client.level == null) activation.reset();
            boolean active = activation.update(mode, presses, KeyBinds.AUTO_ATTACK.isDown(), allowed);
            if (active != autoAttackEnabled && mode == ru.wilyfox.client.hud.config.ClickerConfig.Activation.TOGGLE && allowed) {
                ru.wilyfox.client.audio.UiSounds.toggle();
                info(LOGGER, "Auto {}: {}", getActionName(), active);
            }
            autoAttackEnabled = active;
            if (!active) {
                releaseHeldKey(client);
                lastAttackTime = 0L;
                return;
            }

            if (ConfigManager.get().clicker.actionMode == ru.wilyfox.client.hud.config.ClickerConfig.ActionMode.HOLD) {
                try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("tick/Clicker/hold")) {
                    KeyMapping action = getActionKey(client);
                    if (heldKey != action) {
                        releaseHeldKey(client);
                        heldKey = action;
                        // A real button press starts the action once; subsequent ticks only keep it down.
                        KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.getKey(action.saveString()));
                    }
                    // Toggle Attack/Use interprets setDown(true) as another press; HOLD owns a state.
                    ((ru.wilyfox.mixin.KeyMappingAccessorMixin) heldKey).froghelper$setDownDirect(true);
                    lastAttackTime = 0;
                    return;
                }
            }
            releaseHeldKey(client);

            long now = System.currentTimeMillis();

            int cps = ConfigManager.get().clicker.cps;
            long interval = Math.max(1L, 1000L / Math.max(1, cps));

            if (now - lastAttackTime >= interval) {
                lastAttackTime = now;
                click();
            }
        }
    }
    private static void releaseHeldKey(Minecraft client) {
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("tick/Clicker/release")) {
            if (heldKey == null) return;
            var key = heldKey; heldKey = null;
            var input = com.mojang.blaze3d.platform.InputConstants.getKey(key.saveString());
            boolean physical = false;
            if (client.getWindow() != null && client.isWindowActive()) {
                if (input.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE)
                    physical = org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().handle(), input.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                else if (input.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM && input.getValue() >= 0)
                    physical = com.mojang.blaze3d.platform.InputConstants.isKeyDown(client.getWindow(), input.getValue());
            }
            ((ru.wilyfox.mixin.KeyMappingAccessorMixin) key).froghelper$setDownDirect(physical);
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
