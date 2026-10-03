package ru.wilyfox.client.quickaccess;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.keybinds.KeyBinds;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.mixin.KeyMappingAccessorMixin;

public final class QuickAccessInputHandler {
    private boolean quickAccessHeld;

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("tick/QuickAccessInputHandler")) {
            boolean enabled = ConfigManager.get().quickAccess.active;
            boolean currentlyHeld = enabled && isPhysicallyHeld(client, KeyBinds.QUICK_ACCESS);

            if (currentlyHeld && !quickAccessHeld) {
                QuickAccessManager.getInstance().open(client);
            } else if (!currentlyHeld && quickAccessHeld) {
                QuickAccessManager.getInstance().release(client);
            }

            quickAccessHeld = currentlyHeld;

            if (!enabled && client.gui.screen() instanceof QuickAccessScreen) {
                QuickAccessManager.getInstance().forceClose(client);
            }
        }
    }

    private boolean isPhysicallyHeld(Minecraft client, KeyMapping keyMapping) {
        if (client == null || client.getWindow() == null) {
            return false;
        }

        if (keyMapping == null) {
            return false;
        }

        int keyCode = ((KeyMappingAccessorMixin) keyMapping).froghelper$getKey().getValue();
        if (keyCode < 0) {
            return false;
        }

        com.mojang.blaze3d.platform.Window window = client.getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, keyCode);
    }
}
