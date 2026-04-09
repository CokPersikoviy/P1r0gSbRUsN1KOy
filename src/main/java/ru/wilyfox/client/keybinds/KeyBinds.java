package ru.wilyfox.client.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KeyBinds {
    public static final String CATEGORY = "FrogHelper";

    public static final KeyMapping AUTO_ATTACK = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "Clicker",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_K,
                    CATEGORY
            )
    );


    public static final KeyMapping EDITING_MODE = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "Editing Mode",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_H,
                    CATEGORY
            )
    );

    public static final KeyMapping QUICK_ACCESS = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "Quick Access",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_R,
                    CATEGORY
            )
    );

    public static final KeyMapping SETTINGS_MENU = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "Settings Menu",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_O,
                    CATEGORY
            )
    );

    public static final KeyMapping PING_MARKER = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "Ping Marker",
                    InputConstants.Type.MOUSE,
                    GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                    CATEGORY
            )
    );

    public static final KeyMapping CLAN_HIDE = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "Clan Hide",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_Z,
                    CATEGORY
            )
    );

    public static final KeyMapping RUNES_BAG = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "Runes Bag",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_X,
                    CATEGORY
            )
    );

    private KeyBinds() {

    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(KeyBinds::handleCommandKeybinds);
    }

    private static KeyMapping registerRuneSetSelect(String name, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        name,
                        InputConstants.Type.KEYSYM,
                        keyCode,
                        CATEGORY
                )
        );
    }

    private static void handleCommandKeybinds(Minecraft client) {
        if (client.player == null || client.player.connection == null) {
            return;
        }

        while (CLAN_HIDE.consumeClick()) {
            client.player.connection.sendCommand("clanhide");
        }

        while (RUNES_BAG.consumeClick()) {
            client.player.connection.sendCommand("runesbag");
        }
    }
}
