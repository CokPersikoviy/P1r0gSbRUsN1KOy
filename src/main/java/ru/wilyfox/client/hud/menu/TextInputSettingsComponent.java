package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class TextInputSettingsComponent extends SettingsComponent {
    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private final int maxLength;

    private boolean focused;
    private int cursorPosition;
    private long blinkStartedAt = System.currentTimeMillis();

    public TextInputSettingsComponent(
            int x,
            int y,
            int width,
            int height,
            String label,
            Supplier<String> getter,
            Consumer<String> setter,
            int maxLength
    ) {
        super(x, y, width, height, label);
        this.getter = getter;
        this.setter = setter;
        this.maxLength = Math.max(1, maxLength);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        boolean hovered = isHovered(mouseX, mouseY);

        int rowBg = hovered || focused ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT;
        int textColor = focused ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY;
        int boxBg = focused ? WidgetTheme.PANEL_BG_SOFT : WidgetTheme.BAR_BG;
        int boxTop = focused ? WidgetTheme.ACCENT_LINE : WidgetTheme.PANEL_BG;

        HudSurface.fillRounded(context, x, y, width, height, 4, rowBg);

        int textY = y + (height - mc.font.lineHeight) / 2;
        context.drawString(mc.font, label, x + 8, textY, textColor);

        int boxWidth = Math.max(120, width / 2);
        int boxX = x + width - 8 - boxWidth;
        int boxHeight = height - 6;
        int boxY = y + 3;

        HudSurface.fillRounded(context, boxX, boxY, boxWidth, boxHeight, 3, boxBg);
        context.fill(boxX + 3, boxY, boxX + boxWidth - 3, boxY + 1, boxTop);

        String value = getValue();
        DisplayText display = focused
                ? createFocusedDisplay(mc, value, boxWidth - 10)
                : createUnfocusedDisplay(mc, value, boxWidth - 10);
        int valueX = boxX + 5;
        int valueY = boxY + (boxHeight - mc.font.lineHeight) / 2;

        context.drawString(mc.font, display.text(), valueX, valueY, value.isBlank() ? WidgetTheme.TEXT_MUTED : WidgetTheme.TEXT_SOFT);

        if (focused && shouldShowCursor()) {
            int visibleCursor = Math.max(0, Math.min(display.text().length(), cursorPosition - display.startIndex() + display.prefixLength()));
            int cursorX = valueX + mc.font.width(display.text().substring(0, visibleCursor));
            context.fill(cursorX, valueY, cursorX + 1, valueY + mc.font.lineHeight, WidgetTheme.TEXT_SOFT);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        boolean inside = isHovered(mouseX, mouseY);
        focused = inside;
        if (focused) {
            cursorPosition = getValue().length();
            blinkStartedAt = System.currentTimeMillis();
        }
        return inside;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) {
            return false;
        }

        String value = getValue();

        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            focused = false;
            return true;
        }

        if (ScreenKeyBindings.isSelectAll(keyCode, modifiers)) {
            cursorPosition = value.length();
            return true;
        }

        if (ScreenKeyBindings.isPaste(keyCode, modifiers)) {
            insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }

        if (ScreenKeyBindings.isCopy(keyCode, modifiers)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
            return true;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursorPosition > 0 && !value.isEmpty()) {
                    setValue(value.substring(0, cursorPosition - 1) + value.substring(cursorPosition));
                    cursorPosition--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursorPosition < value.length()) {
                    setValue(value.substring(0, cursorPosition) + value.substring(cursorPosition + 1));
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursorPosition = Math.max(0, cursorPosition - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursorPosition = Math.min(value.length(), cursorPosition + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursorPosition = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursorPosition = value.length();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!focused || Character.isISOControl(codePoint)) {
            return false;
        }

        insertText(String.valueOf(codePoint));
        return true;
    }

    @Override
    public void onClickOutside() {
        focused = false;
    }

    private void insertText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch >= 32 && ch != 127 && ch != '\n' && ch != '\r') {
                builder.append(ch);
            }
        }

        if (builder.isEmpty()) {
            return;
        }

        String value = getValue();
        String insert = builder.toString();
        int allowed = maxLength - value.length();
        if (allowed <= 0) {
            return;
        }

        if (insert.length() > allowed) {
            insert = insert.substring(0, allowed);
        }

        String updated = value.substring(0, cursorPosition) + insert + value.substring(cursorPosition);
        setValue(updated);
        cursorPosition = Math.min(updated.length(), cursorPosition + insert.length());
        blinkStartedAt = System.currentTimeMillis();
    }

    private String getValue() {
        String value = getter.get();
        return value == null ? "" : value;
    }

    private void setValue(String value) {
        setter.accept(value);
        ConfigManager.save();
    }

    private DisplayText createUnfocusedDisplay(Minecraft mc, String text, int maxWidth) {
        if (mc.font.width(text) <= maxWidth) {
            return new DisplayText(text, 0, 0);
        }

        String ellipsis = "...";
        for (int end = text.length(); end >= 0; end--) {
            String candidate = text.substring(0, end) + ellipsis;
            if (mc.font.width(candidate) <= maxWidth) {
                return new DisplayText(candidate, 0, 0);
            }
        }

        return new DisplayText(ellipsis, 0, 0);
    }

    private DisplayText createFocusedDisplay(Minecraft mc, String text, int maxWidth) {
        if (mc.font.width(text) <= maxWidth) {
            return new DisplayText(text, 0, 0);
        }

        String ellipsis = "...";
        int textLength = text.length();
        int safeCursor = Math.max(0, Math.min(cursorPosition, textLength));
        int start = safeCursor;
        int end = safeCursor;

        while (start > 0 && mc.font.width(buildDisplayText(text, start - 1, end)) <= maxWidth) {
            start--;
        }

        while (end < textLength && mc.font.width(buildDisplayText(text, start, end + 1)) <= maxWidth) {
            end++;
        }

        while (start > 0) {
            String candidate = buildDisplayText(text, start - 1, end);
            if (mc.font.width(candidate) > maxWidth) {
                break;
            }
            start--;
        }

        while (end < textLength) {
            String candidate = buildDisplayText(text, start, end + 1);
            if (mc.font.width(candidate) > maxWidth) {
                break;
            }
            end++;
        }

        String visible = buildDisplayText(text, start, end);
        int prefixLength = start > 0 ? ellipsis.length() : 0;
        return new DisplayText(visible, start, prefixLength);
    }

    private String buildDisplayText(String text, int start, int end) {
        StringBuilder builder = new StringBuilder();
        if (start > 0) {
            builder.append("...");
        }
        builder.append(text, start, end);
        if (end < text.length()) {
            builder.append("...");
        }
        return builder.toString();
    }

    private boolean shouldShowCursor() {
        return ((System.currentTimeMillis() - blinkStartedAt) / 500L) % 2L == 0L;
    }

    private record DisplayText(String text, int startIndex, int prefixLength) {
    }

    private static final class ScreenKeyBindings {
        private static boolean isPaste(int keyCode, int modifiers) {
            return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V;
        }

        private static boolean isCopy(int keyCode, int modifiers) {
            return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_C;
        }

        private static boolean isSelectAll(int keyCode, int modifiers) {
            return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_A;
        }
    }
}
