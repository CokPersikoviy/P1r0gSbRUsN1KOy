package ru.wilyfox.client.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.clan.ClanSiegeMapScreen;

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

    // public static final KeyMapping PING_MARKER = KeyBindingHelper.registerKeyBinding(
    //         new KeyMapping(
    //                 "Ping Marker",
    //                 InputConstants.Type.MOUSE,
    //                 GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
    //                 CATEGORY
    //         )
    // );

    public static final KeyMapping CLAN_HIDE = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "Clan Hide",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_Z,
                    CATEGORY
            )
    );

    public static final KeyMapping CLAN_SIEGE_MAP = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "Clan Siege Map",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_M,
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

    public static final KeyMapping SOCIAL = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "Social",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_C,
                    CATEGORY
            )
    );

    // NOTE: rune-set selection (number row 1-7) is intentionally NOT a registered KeyMapping.
    // MC keeps a single KeyMapping per key, so binding 1-7 here would shadow the vanilla hotbar keys
    // globally (and can't be "only in the rune bag"). Instead RuneSetSwitcher reads raw 1-7 directly
    // inside the rune-bag screen, so the hotbar keeps working everywhere.

    private KeyBinds() {

    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(KeyBinds::handleCommandKeybinds);
    }

    private static void handleCommandKeybinds(Minecraft client) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("tick/KeyBinds")) {
            if (client.player == null || client.player.connection == null) {
                return;
            }

            while (CLAN_HIDE.consumeClick()) {
                client.player.connection.sendCommand("clanhide");
            }

            while (CLAN_SIEGE_MAP.consumeClick()) {
                if (ClanSiegeMapScreen.canOpen()) {
                    client.setScreen(new ClanSiegeMapScreen());
                }
            }

            while (RUNES_BAG.consumeClick()) {
                client.player.connection.sendCommand("runesbag");
            }

            while (SOCIAL.consumeClick()) {
                client.setScreen(new ru.wilyfox.client.moduser.SocialScreen());
            }
        }
    }
}
