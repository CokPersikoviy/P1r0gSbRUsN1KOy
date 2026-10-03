package ru.wilyfox.utils;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class InputModifiers {
    private InputModifiers() {}

    public static boolean hasShiftDown() {
        return down(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean hasAltDown() {
        return down(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    public static boolean hasControlDown() {
        return down(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean down(int left, int right) {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, left) || InputConstants.isKeyDown(window, right);
    }
}
