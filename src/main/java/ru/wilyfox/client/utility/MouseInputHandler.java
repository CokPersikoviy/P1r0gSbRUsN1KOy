package ru.wilyfox.client.utility;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.HudRenderer;
import ru.wilyfox.client.profiler.ModProfiler;

import java.util.Arrays;

import static ru.wilyfox.utils.MouseUtils.getMouseX;
import static ru.wilyfox.utils.MouseUtils.getMouseY;

public final class MouseInputHandler {
    private final HudRenderer hudRenderer;

    private final boolean[] mouseWasPressed = new boolean[GLFW.GLFW_MOUSE_BUTTON_LAST + 1];

    public MouseInputHandler(HudRenderer h) {
        this.hudRenderer = h;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("tick/MouseInputHandler")) {
                if (client.player == null) {
                    return;
                }

                if (!hudRenderer.isEditing() && !hudRenderer.isSettingsOpen()) {
                    Arrays.fill(mouseWasPressed, false);
                    return;
                }

                long window = client.getWindow().getWindow();
                double mouseX = getMouseX();
                double mouseY = getMouseY();

                int screenWidth = client.getWindow().getGuiScaledWidth();
                int screenHeight = client.getWindow().getGuiScaledHeight();

                for (int button = GLFW.GLFW_MOUSE_BUTTON_1; button <= GLFW.GLFW_MOUSE_BUTTON_LAST; button++) {
                    boolean pressed = GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
                    if (pressed && !mouseWasPressed[button]) {
                        hudRenderer.onMousePressed(mouseX, mouseY, button);
                    }

                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pressed) {
                        hudRenderer.onMouseDragged(mouseX, mouseY, screenWidth, screenHeight, button);
                    }

                    if (!pressed && mouseWasPressed[button]) {
                        hudRenderer.onMouseReleased(button, screenWidth, screenHeight, mouseX, mouseY);
                    }

                    mouseWasPressed[button] = pressed;
                }
            }
        });
    }
}
