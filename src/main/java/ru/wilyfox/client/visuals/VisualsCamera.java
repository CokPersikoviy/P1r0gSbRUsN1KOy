package ru.wilyfox.client.visuals;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.RunesBagConfig;
import ru.wilyfox.client.hud.config.VisualsConfig;

public final class VisualsCamera {
    private static boolean active, wasDown, toggled, hitWasDown;
    private static float yaw, pitch;
    private static CameraType previous;
    private VisualsCamera() {}
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            tick(mc, down(mc, ConfigManager.get().visuals.camera.key) && modifiers(ConfigManager.get().visuals.camera.modifiers));
            var hit = ConfigManager.get().visuals.hitboxes;
            boolean pressed = mc.player != null && mc.gui.screen() == null && mc.isWindowActive() && down(mc, hit.key) && modifiers(hit.modifiers);
            if (pressed && !hitWasDown) { hit.show = !hit.show; ru.wilyfox.client.audio.UiSounds.toggle(); ConfigManager.save(); }
            hitWasDown = pressed;
            VisualsTab.tick(mc);
            CompactReload.tick(mc);
        });
    }
    public static boolean modifiers(int requested) {
        int held = (ru.wilyfox.utils.InputModifiers.hasShiftDown() ? 1 : 0)
                | (ru.wilyfox.utils.InputModifiers.hasControlDown() ? 2 : 0)
                | (ru.wilyfox.utils.InputModifiers.hasAltDown() ? 4 : 0);
        return (held & requested) == requested;
    }
    public static boolean down(Minecraft mc, int code) {
        if (!(code >= 32 && code <= GLFW.GLFW_KEY_LAST || code >= RunesBagConfig.MOUSE_CODE_OFFSET && code <= RunesBagConfig.MOUSE_CODE_OFFSET + GLFW.GLFW_MOUSE_BUTTON_LAST)) return false;
        return RunesBagConfig.isMouseCode(code)
                ? GLFW.glfwGetMouseButton(mc.getWindow().handle(), RunesBagConfig.mouseButton(code)) == GLFW.GLFW_PRESS
                : InputConstants.isKeyDown(mc.getWindow(), code);
    }
    public static void tick(Minecraft mc, boolean down) {
        var c = ConfigManager.get().visuals.camera;
        if (!c.enabled || mc.player == null || mc.gui.screen() != null || !mc.isWindowActive()) {
            stop(mc); wasDown = down; return;
        }
        if (down && !wasDown && c.mode == VisualsConfig.LookMode.TOGGLE) toggled = !toggled;
        wasDown = down;
        boolean wanted = c.mode == VisualsConfig.LookMode.HOLD ? down : toggled;
        if (wanted && !active) {
            previous = mc.options.getCameraType(); yaw = mc.player.getYRot(); pitch = mc.player.getXRot();
            mc.options.setCameraType(c.frontView ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK); active = true;
        } else if (!wanted && active) stop(mc);
    }
    private static void stop(Minecraft mc) {
        if (active && previous != null) mc.options.setCameraType(previous);
        active = false; toggled = false; previous = null;
    }
    public static boolean active() { return active; }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }
    public static void turn(double dx, double dy) {
        yaw += (float) (dx * 0.15);
        pitch = Math.clamp(pitch + (float) (dy * 0.15 * (ConfigManager.get().visuals.camera.invertPitch ? -1 : 1)), -90, 90);
    }
    public static float zoom(float vanilla) {
        var c = ConfigManager.get().visuals.camera;
        return active ? (float) c.distance : c.distanceEnabled ? (float) c.thirdPersonDistance : vanilla;
    }
}
