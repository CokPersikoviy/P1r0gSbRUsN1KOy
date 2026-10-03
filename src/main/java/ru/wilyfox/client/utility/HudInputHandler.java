package ru.wilyfox.client.utility;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.HudRenderer;
import ru.wilyfox.client.keybinds.KeyBinds;
import ru.wilyfox.client.profiler.ModProfiler;

public final class HudInputHandler {
    private final HudRenderer hudRenderer;

    public HudInputHandler(HudRenderer hudRenderer) {
        this.hudRenderer = hudRenderer;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("tick/HudInputHandler")) {
                while (KeyBinds.EDITING_MODE.consumeClick()) {
                    toggleEditor(client);
                }

                while (KeyBinds.SETTINGS_MENU.consumeClick()) {
                    toggleSettings(client);
                }
            }
        });
    }

    private void toggleEditor(Minecraft client) {
        if (client.gui.screen() instanceof HudEditingScreen) {
            client.gui.setScreen(null);
            hudRenderer.setEditing(false);
            return;
        }

        hudRenderer.setEditing(true);
        client.gui.setScreen(new HudEditingScreen(hudRenderer));
    }

    private void toggleSettings(Minecraft client) {
        hudRenderer.toggleSettings();

        if (hudRenderer.isSettingsOpen()) {
            hudRenderer.setEditing(false);
            // Replacing the editor invokes removed(), which would clear the settings flag.
            if (!(client.gui.screen() instanceof HudEditingScreen)) {
                client.gui.setScreen(new HudEditingScreen(hudRenderer));
            }
        } else {
            client.gui.setScreen(null);
        }
    }
}
