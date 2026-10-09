package ru.wilyfox.client.quickaccess;

import net.minecraft.client.Minecraft;

public final class QuickAccessManager {
    private static final QuickAccessManager INSTANCE = new QuickAccessManager();

    private QuickAccessScreen activeScreen;
    private boolean heldOpen;

    private QuickAccessManager() {
    }

    public static QuickAccessManager getInstance() {
        return INSTANCE;
    }

    public void open(Minecraft client) {
        if (client == null || client.gui.screen() != null) {
            return;
        }

        QuickAccessScreen screen = new QuickAccessScreen();
        activeScreen = screen;
        heldOpen = true;
        client.gui.setScreen(screen);
    }

    public void release(Minecraft client) {
        if (!heldOpen) {
            return;
        }

        heldOpen = false;
        if (!(client.gui.screen() instanceof QuickAccessScreen screen) || screen != activeScreen) {
            activeScreen = null;
            return;
        }

        QuickAccessItemConfig hovered = screen.getHoveredItem();
        client.gui.setScreen(null);
        activeScreen = null;

        if (hovered == null) {
            return;
        }

        String resolved = QuickAccessCommandResolver.resolveCommand(hovered);
        if (resolved == null || resolved.isBlank()) {
            if (QuickAccessCommandResolver.requiresTarget(hovered)) {
                QuickAccessCommandResolver.showMissingTargetFeedback();
                ru.wilyfox.client.audio.UiSounds.play(ru.wilyfox.client.audio.UiSound.WARNING);
            }
            return;
        }

        if (client.player != null && client.player.connection != null) {
            // Explicit player actions follow the same path as typing a command; automated chat
            // and its server-reported message cooldown must not delay a teleport/menu action.
            client.player.connection.sendCommand(resolved);
            ru.wilyfox.client.audio.UiSounds.click();
        }
    }

    public void forceClose(Minecraft client) {
        heldOpen = false;
        if (client.gui.screen() == activeScreen) {
            client.gui.setScreen(null);
        }
        activeScreen = null;
    }
}
