package ru.wilyfox.client.hud.config;

import org.lwjgl.glfw.GLFW;

/**
 * Keybinds for selecting rune sets inside the rune bag. Stored here (in FrogHelper's own config and
 * rebound in the "Runes Bag Keybinds" settings tab) rather than as vanilla KeyMappings — a registered
 * KeyMapping keeps a single owner per key, so binding 1-7 there would shadow the vanilla hotbar keys
 * globally. These are only consulted inside the rune-bag screen, so they never collide with gameplay.
 */
public class RunesBagConfig {
    /**
     * A selector bind stored as a mouse button is encoded as {@code MOUSE_CODE_OFFSET + glfwButton}.
     * GLFW keycodes top out around {@code GLFW_KEY_LAST} (348), so this offset never collides with a
     * real keycode and lets one int field hold either a keyboard key or a mouse button.
     */
    public static final int MOUSE_CODE_OFFSET = 1000;

    /** GLFW keycodes (or {@code MOUSE_CODE_OFFSET + button}) for rune-set selectors 1..7. Default = 1-7. */
    public int[] setSelectorKeys = defaultSelectorKeys();

    public static boolean isMouseCode(int code) {
        return code >= MOUSE_CODE_OFFSET;
    }

    public static int mouseButton(int code) {
        return code - MOUSE_CODE_OFFSET;
    }

    public static int[] defaultSelectorKeys() {
        return new int[]{
                GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4,
                GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7
        };
    }
}
