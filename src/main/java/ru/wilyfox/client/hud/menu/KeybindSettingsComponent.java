package ru.wilyfox.client.hud.menu;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.RunesBagConfig;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A rebindable key row: shows the label and the current key; left-click arms "listening" and the
 * next key press is captured; right-click clears the bind; Esc while listening cancels. Keys are
 * plain GLFW keycodes stored in config — NOT vanilla KeyMappings — so they never shadow other keys.
 */
public class KeybindSettingsComponent extends SettingsComponent {
    private final IntSupplier getter;
    private final IntConsumer setter;
    private boolean listening = false;

    public KeybindSettingsComponent(int x, int y, int width, int height, String label,
                                    IntSupplier getter, IntConsumer setter) {
        super(x, y, width, height, label);
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        boolean hovered = isHovered(mouseX, mouseY);
        int rowBg = hovered ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT;
        int textColor = hovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY;

        HudSurface.fillRounded(context, x, y, width, height, 4, rowBg);

        int textY = y + (height - mc.font.lineHeight) / 2;
        context.drawString(mc.font, label, x + 8, textY, textColor);

        String keyText = listening ? "> ... <" : keyName(getter.getAsInt());
        int boxWidth = Math.max(52, Math.min(128, mc.font.width(keyText) + 16));
        int boxX = x + width - 8 - boxWidth;
        int boxBg = listening || hovered ? WidgetTheme.PANEL_BG : WidgetTheme.BAR_BG;

        HudSurface.fillRounded(context, boxX, y + 3, boxWidth, height - 6, 3, boxBg);
        if (listening) {
            context.fill(boxX + 3, y + 3, boxX + boxWidth - 3, y + 4, WidgetTheme.ACCENT_LINE);
        }
        int keyColor = listening ? WidgetTheme.STATUS_WARNING : WidgetTheme.TITLE;
        context.drawCenteredString(mc.font, keyText, boxX + boxWidth / 2, textY, keyColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // While listening, an extra mouse button (middle / mouse4 / mouse5) binds to it. Left/right stay
        // reserved for arm/clear, so only buttons >= 2 are captured. No hover needed — like a key press.
        if (listening && button >= 2) {
            setter.accept(RunesBagConfig.MOUSE_CODE_OFFSET + button);
            ConfigManager.save();
            listening = false;
            return true;
        }
        if (!isHovered(mouseX, mouseY)) {
            return false;
        }
        if (button == 1) {
            setter.accept(GLFW.GLFW_KEY_UNKNOWN); // right-click clears
            ConfigManager.save();
            listening = false;
            return true;
        }
        if (button == 0) {
            listening = !listening;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!listening) {
            return false;
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) { // Esc cancels without changing the bind
            setter.accept(keyCode);
            ConfigManager.save();
        }
        listening = false;
        return true;
    }

    private static String keyName(int code) {
        if (code == GLFW.GLFW_KEY_UNKNOWN) {
            return "—";
        }
        if (RunesBagConfig.isMouseCode(code)) {
            return "Mouse " + (RunesBagConfig.mouseButton(code) + 1); // 0-based GLFW button -> 1-based label
        }
        return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
    }
}
