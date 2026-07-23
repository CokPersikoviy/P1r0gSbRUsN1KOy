package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class ColorPickerSettingsComponent extends SettingsComponent {
    private static final int COLLAPSED_HEIGHT = 22;
    private static final int EXPANDED_HEIGHT = 132;
    private static final int ROW_HEIGHT = 22;
    private static final int HEX_WIDTH = 72;
    private static final int SWATCH_SIZE = 16;
    private static final int PENCIL_SIZE = 16;
    private static final int CONTROL_GAP = 5;
    private static final int PALETTE_HEIGHT = 68;
    private static final int HUE_HEIGHT = 8;

    private final IntSupplier getter;
    private final IntConsumer setter;

    private boolean expanded;
    private boolean hexFocused;
    private boolean selectAll;
    private boolean dirty;
    private boolean labelTruncated;
    private DragTarget dragTarget = DragTarget.NONE;
    private String hexDraft = "#000000";
    private int cursorPosition = hexDraft.length();
    private int lastColor = -1;
    private float hue;
    private float saturation;
    private float value;
    private long blinkStartedAt = System.currentTimeMillis();

    public ColorPickerSettingsComponent(String label, IntSupplier getter, IntConsumer setter) {
        super(0, 0, 0, COLLAPSED_HEIGHT, label);
        this.getter = getter;
        this.setter = setter;
        this.preferredHeight = COLLAPSED_HEIGHT;
    }

    @Override
    public int getPreferredHeight() {
        return expanded ? EXPANDED_HEIGHT : COLLAPSED_HEIGHT;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY) {
        syncFromConfig();

        Minecraft minecraft = Minecraft.getInstance();
        boolean hovered = isHovered(mouseX, mouseY);
        int rowBackground = hovered || expanded || hexFocused
                ? WidgetTheme.PANEL_BG
                : WidgetTheme.PANEL_BG_SOFT;

        HudSurface.fillRounded(context, x, y, width, height, 4, rowBackground);

        int pencilX = pencilX();
        int swatchX = swatchX();
        int hexX = hexX();
        int textY = y + (ROW_HEIGHT - minecraft.font.lineHeight) / 2;
        int labelMaxWidth = Math.max(0, hexX - (x + 8) - 5);
        labelTruncated = minecraft.font.width(label) > labelMaxWidth;
        String displayLabel = labelTruncated
                ? minecraft.font.plainSubstrByWidth(label, Math.max(0, labelMaxWidth - minecraft.font.width("..."))) + "..."
                : label;
        context.drawString(
                minecraft.font,
                displayLabel,
                x + 8,
                textY,
                hovered || expanded ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY
        );

        renderHexField(context, minecraft, hexX, mouseX, mouseY);
        renderSwatch(context, swatchX, mouseX, mouseY);
        renderPencil(context, minecraft, pencilX, mouseX, mouseY);

        if (expanded) {
            renderPicker(context, minecraft);
        }
    }

    @Override
    public String getTooltip(int mouseX, int mouseY) {
        return labelTruncated
                && mouseX >= x + 8
                && mouseX < hexX()
                && mouseY >= y
                && mouseY <= y + ROW_HEIGHT
                ? label
                : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) {
            return false;
        }

        if (contains(mouseX, mouseY, swatchX(), swatchY(), SWATCH_SIZE, SWATCH_SIZE)
                || contains(mouseX, mouseY, pencilX(), pencilY(), PENCIL_SIZE, PENCIL_SIZE)) {
            finishHexEditing();
            expanded = !expanded;
            dragTarget = DragTarget.NONE;
            return true;
        }

        if (contains(mouseX, mouseY, hexX(), controlY(), HEX_WIDTH, controlHeight())) {
            hexFocused = true;
            selectAll = true;
            cursorPosition = hexDraft.length();
            blinkStartedAt = System.currentTimeMillis();
            return true;
        }

        finishHexEditing();
        if (expanded && contains(mouseX, mouseY, paletteX(), paletteY(), paletteWidth(), PALETTE_HEIGHT)) {
            dragTarget = DragTarget.PALETTE;
            updatePalette(mouseX, mouseY);
            return true;
        }
        if (expanded && contains(mouseX, mouseY, hueX(), hueY(), hueWidth(), HUE_HEIGHT)) {
            dragTarget = DragTarget.HUE;
            updateHue(mouseX);
            return true;
        }

        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0 || dragTarget == DragTarget.NONE) {
            return false;
        }

        if (dragTarget == DragTarget.PALETTE) {
            updatePalette(mouseX, mouseY);
        } else {
            updateHue(mouseX);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || dragTarget == DragTarget.NONE) {
            return false;
        }

        dragTarget = DragTarget.NONE;
        saveIfDirty();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!hexFocused) {
            if (expanded && keyCode == GLFW.GLFW_KEY_ESCAPE) {
                expanded = false;
                return true;
            }
            return false;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            revertHexDraft();
            hexFocused = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            finishHexEditing();
            return true;
        }
        if (isShortcut(modifiers, keyCode, GLFW.GLFW_KEY_A)) {
            selectAll = true;
            cursorPosition = hexDraft.length();
            return true;
        }
        if (isShortcut(modifiers, keyCode, GLFW.GLFW_KEY_C)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(hexDraft);
            return true;
        }
        if (isShortcut(modifiers, keyCode, GLFW.GLFW_KEY_V)) {
            pasteHex(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                deleteBeforeCursor();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                deleteAtCursor();
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                selectAll = false;
                cursorPosition = Math.max(1, cursorPosition - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                selectAll = false;
                cursorPosition = Math.min(hexDraft.length(), cursorPosition + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                selectAll = false;
                cursorPosition = 1;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                selectAll = false;
                cursorPosition = hexDraft.length();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!hexFocused || !isHexDigit(codePoint)) {
            return false;
        }

        insertHexDigit(Character.toUpperCase(codePoint));
        return true;
    }

    @Override
    public void onClickOutside() {
        finishHexEditing();
        expanded = false;
        if (dragTarget != DragTarget.NONE) {
            dragTarget = DragTarget.NONE;
            saveIfDirty();
        }
    }

    private void renderHexField(GuiGraphics context, Minecraft minecraft, int hexX, int mouseX, int mouseY) {
        int boxY = controlY();
        int boxHeight = controlHeight();
        boolean hovered = contains(mouseX, mouseY, hexX, boxY, HEX_WIDTH, boxHeight);
        boolean valid = parseHex(hexDraft) != null;

        HudSurface.fillRounded(
                context,
                hexX,
                boxY,
                HEX_WIDTH,
                boxHeight,
                3,
                hovered || hexFocused ? WidgetTheme.PANEL_BG_SOFT : WidgetTheme.BAR_BG
        );
        int topColor = hexFocused
                ? valid ? WidgetTheme.ACCENT_LINE : WidgetTheme.HARD_ACCENT
                : WidgetTheme.PANEL_BG;
        context.fill(hexX + 3, boxY, hexX + HEX_WIDTH - 3, boxY + 1, topColor);

        int valueX = hexX + 5;
        int valueY = boxY + (boxHeight - minecraft.font.lineHeight) / 2;
        if (hexFocused && selectAll) {
            int selectionWidth = Math.min(HEX_WIDTH - 10, minecraft.font.width(hexDraft));
            context.fill(
                    valueX,
                    valueY - 1,
                    valueX + selectionWidth,
                    valueY + minecraft.font.lineHeight,
                    WidgetTheme.ACCENT_LINE
            );
        }
        context.drawString(
                minecraft.font,
                hexDraft,
                valueX,
                valueY,
                valid ? WidgetTheme.TEXT_SOFT : WidgetTheme.STATUS_ERROR
        );

        if (hexFocused && !selectAll && shouldShowCursor()) {
            int safeCursor = Math.max(1, Math.min(cursorPosition, hexDraft.length()));
            int cursorX = valueX + minecraft.font.width(hexDraft.substring(0, safeCursor));
            context.fill(cursorX, valueY, cursorX + 1, valueY + minecraft.font.lineHeight, WidgetTheme.TEXT_SOFT);
        }
    }

    private void renderSwatch(GuiGraphics context, int swatchX, int mouseX, int mouseY) {
        int swatchY = swatchY();
        boolean hovered = contains(mouseX, mouseY, swatchX, swatchY, SWATCH_SIZE, SWATCH_SIZE);
        int border = hovered || expanded ? WidgetTheme.TITLE : WidgetTheme.TEXT_MUTED;
        HudSurface.fillRounded(context, swatchX, swatchY, SWATCH_SIZE, SWATCH_SIZE, 3, border);
        HudSurface.fillRounded(
                context,
                swatchX + 2,
                swatchY + 2,
                SWATCH_SIZE - 4,
                SWATCH_SIZE - 4,
                2,
                0xFF000000 | currentColor()
        );
    }

    private void renderPencil(GuiGraphics context, Minecraft minecraft, int pencilX, int mouseX, int mouseY) {
        int pencilY = pencilY();
        boolean hovered = contains(mouseX, mouseY, pencilX, pencilY, PENCIL_SIZE, PENCIL_SIZE);
        HudSurface.fillRounded(
                context,
                pencilX,
                pencilY,
                PENCIL_SIZE,
                PENCIL_SIZE,
                3,
                hovered || expanded ? WidgetTheme.PANEL_BG_SOFT : WidgetTheme.BAR_BG
        );
        context.drawCenteredString(
                minecraft.font,
                "\u270E",
                pencilX + PENCIL_SIZE / 2,
                pencilY + (PENCIL_SIZE - minecraft.font.lineHeight) / 2,
                hovered || expanded ? WidgetTheme.TITLE : WidgetTheme.TEXT_SECONDARY
        );
    }

    private void renderPicker(GuiGraphics context, Minecraft minecraft) {
        int paletteX = paletteX();
        int paletteY = paletteY();
        int paletteWidth = paletteWidth();

        for (int offset = 0; offset < paletteWidth; offset++) {
            float localSaturation = paletteWidth <= 1 ? 0.0F : offset / (float) (paletteWidth - 1);
            int topColor = 0xFF000000 | hsvToRgb(hue, localSaturation, 1.0F);
            context.fillGradient(
                    paletteX + offset,
                    paletteY,
                    paletteX + offset + 1,
                    paletteY + PALETTE_HEIGHT,
                    topColor,
                    0xFF000000
            );
        }

        int selectionX = paletteX + Math.round(saturation * Math.max(0, paletteWidth - 1));
        int selectionY = paletteY + Math.round((1.0F - value) * (PALETTE_HEIGHT - 1));
        context.fill(selectionX - 2, selectionY - 2, selectionX + 3, selectionY + 3, 0xFF000000);
        context.fill(selectionX - 1, selectionY - 1, selectionX + 2, selectionY + 2, 0xFFFFFFFF);
        context.fill(selectionX, selectionY, selectionX + 1, selectionY + 1, 0xFF000000 | currentColor());

        int hueY = hueY();
        int hueWidth = hueWidth();
        for (int offset = 0; offset < hueWidth; offset++) {
            float localHue = hueWidth <= 1 ? 0.0F : offset / (float) (hueWidth - 1);
            int color = 0xFF000000 | hsvToRgb(localHue, 1.0F, 1.0F);
            context.fill(hueX() + offset, hueY, hueX() + offset + 1, hueY + HUE_HEIGHT, color);
        }

        int hueMarkerX = hueX() + Math.round(hue * Math.max(0, hueWidth - 1));
        context.fill(hueMarkerX - 1, hueY - 2, hueMarkerX + 2, hueY + HUE_HEIGHT + 2, 0xFF000000);
        context.fill(hueMarkerX, hueY - 1, hueMarkerX + 1, hueY + HUE_HEIGHT + 1, 0xFFFFFFFF);

        int color = currentColor();
        String rgbText = String.format(
                Locale.ROOT,
                "R %d   G %d   B %d",
                color >> 16 & 0xFF,
                color >> 8 & 0xFF,
                color & 0xFF
        );
        context.drawString(
                minecraft.font,
                rgbText,
                paletteX,
                hueY + HUE_HEIGHT + 6,
                WidgetTheme.TEXT_SECONDARY
        );
    }

    private void updatePalette(double mouseX, double mouseY) {
        saturation = clamp((float) ((mouseX - paletteX()) / Math.max(1.0, paletteWidth() - 1.0)));
        value = 1.0F - clamp((float) ((mouseY - paletteY()) / Math.max(1.0, PALETTE_HEIGHT - 1.0)));
        applyHsvColor();
    }

    private void updateHue(double mouseX) {
        hue = clamp((float) ((mouseX - hueX()) / Math.max(1.0, hueWidth() - 1.0)));
        applyHsvColor();
    }

    private void applyHsvColor() {
        applyColor(hsvToRgb(hue, saturation, value), false);
    }

    private void applyColor(int color, boolean refreshHsv) {
        int rgb = color & 0xFFFFFF;
        setter.accept(rgb);
        lastColor = rgb;
        hexDraft = formatHex(rgb);
        cursorPosition = hexDraft.length();
        selectAll = false;
        dirty = true;
        if (refreshHsv) {
            Hsv hsv = rgbToHsv(rgb);
            hue = hsv.hue();
            saturation = hsv.saturation();
            value = hsv.value();
        }
        WidgetTheme.syncConfiguredTheme();
    }

    private void syncFromConfig() {
        int color = currentColor();
        if (color == lastColor || hexFocused || dragTarget != DragTarget.NONE) {
            return;
        }

        lastColor = color;
        hexDraft = formatHex(color);
        cursorPosition = hexDraft.length();
        Hsv hsv = rgbToHsv(color);
        hue = hsv.hue();
        saturation = hsv.saturation();
        value = hsv.value();
    }

    private void finishHexEditing() {
        if (!hexFocused) {
            return;
        }

        Integer parsed = parseHex(hexDraft);
        if (parsed != null) {
            applyColor(parsed, true);
            saveIfDirty();
        } else {
            revertHexDraft();
        }
        hexFocused = false;
        selectAll = false;
    }

    private void revertHexDraft() {
        hexDraft = formatHex(currentColor());
        cursorPosition = hexDraft.length();
        selectAll = false;
    }

    private void pasteHex(String clipboard) {
        Integer parsed = parseHex(clipboard);
        if (parsed != null) {
            applyColor(parsed, true);
            hexFocused = true;
            cursorPosition = hexDraft.length();
            return;
        }

        if (clipboard == null) {
            return;
        }
        for (int index = 0; index < clipboard.length(); index++) {
            char character = clipboard.charAt(index);
            if (isHexDigit(character)) {
                insertHexDigit(Character.toUpperCase(character));
            }
        }
    }

    private void insertHexDigit(char digit) {
        if (selectAll) {
            hexDraft = "#";
            cursorPosition = 1;
            selectAll = false;
        }
        if (hexDraft.length() >= 7) {
            return;
        }

        int safeCursor = Math.max(1, Math.min(cursorPosition, hexDraft.length()));
        hexDraft = hexDraft.substring(0, safeCursor) + digit + hexDraft.substring(safeCursor);
        cursorPosition = safeCursor + 1;
        blinkStartedAt = System.currentTimeMillis();
        applyDraftIfComplete();
    }

    private void deleteBeforeCursor() {
        if (selectAll) {
            hexDraft = "#";
            cursorPosition = 1;
            selectAll = false;
            return;
        }
        if (cursorPosition <= 1 || hexDraft.length() <= 1) {
            return;
        }

        int safeCursor = Math.min(cursorPosition, hexDraft.length());
        hexDraft = hexDraft.substring(0, safeCursor - 1) + hexDraft.substring(safeCursor);
        cursorPosition = safeCursor - 1;
        blinkStartedAt = System.currentTimeMillis();
    }

    private void deleteAtCursor() {
        if (selectAll) {
            hexDraft = "#";
            cursorPosition = 1;
            selectAll = false;
            return;
        }
        if (cursorPosition < 1 || cursorPosition >= hexDraft.length()) {
            return;
        }

        hexDraft = hexDraft.substring(0, cursorPosition) + hexDraft.substring(cursorPosition + 1);
        blinkStartedAt = System.currentTimeMillis();
    }

    private void applyDraftIfComplete() {
        Integer parsed = parseHex(hexDraft);
        if (parsed != null) {
            applyColor(parsed, true);
            hexFocused = true;
        }
    }

    private void saveIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        ConfigManager.save();
    }

    private int currentColor() {
        return getter.getAsInt() & 0xFFFFFF;
    }

    private int pencilX() {
        return x + width - 8 - PENCIL_SIZE;
    }

    private int pencilY() {
        return y + (ROW_HEIGHT - PENCIL_SIZE) / 2;
    }

    private int swatchX() {
        return pencilX() - CONTROL_GAP - SWATCH_SIZE;
    }

    private int swatchY() {
        return y + (ROW_HEIGHT - SWATCH_SIZE) / 2;
    }

    private int hexX() {
        return swatchX() - CONTROL_GAP - HEX_WIDTH;
    }

    private int controlY() {
        return y + 3;
    }

    private int controlHeight() {
        return ROW_HEIGHT - 6;
    }

    private int paletteX() {
        return x + 8;
    }

    private int paletteY() {
        return y + ROW_HEIGHT + 7;
    }

    private int paletteWidth() {
        return Math.max(1, width - 16);
    }

    private int hueX() {
        return paletteX();
    }

    private int hueY() {
        return paletteY() + PALETTE_HEIGHT + 6;
    }

    private int hueWidth() {
        return paletteWidth();
    }

    private boolean shouldShowCursor() {
        return ((System.currentTimeMillis() - blinkStartedAt) / 500L) % 2L == 0L;
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height;
    }

    private static boolean isShortcut(int modifiers, int keyCode, int expectedKey) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == expectedKey;
    }

    private static boolean isHexDigit(char character) {
        return character >= '0' && character <= '9'
                || character >= 'a' && character <= 'f'
                || character >= 'A' && character <= 'F';
    }

    static String formatHex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    static Integer parseHex(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() != 6) {
            return null;
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (!isHexDigit(normalized.charAt(index))) {
                return null;
            }
        }

        return Integer.parseInt(normalized, 16);
    }

    static Hsv rgbToHsv(int color) {
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;

        float hue;
        if (delta == 0.0F) {
            hue = 0.0F;
        } else if (max == red) {
            hue = ((green - blue) / delta) % 6.0F;
        } else if (max == green) {
            hue = (blue - red) / delta + 2.0F;
        } else {
            hue = (red - green) / delta + 4.0F;
        }
        hue /= 6.0F;
        if (hue < 0.0F) {
            hue += 1.0F;
        }

        float saturation = max == 0.0F ? 0.0F : delta / max;
        return new Hsv(hue, saturation, max);
    }

    static int hsvToRgb(float hue, float saturation, float value) {
        float normalizedHue = clamp(hue);
        float normalizedSaturation = clamp(saturation);
        float normalizedValue = clamp(value);
        float scaledHue = normalizedHue * 6.0F;
        int sector = Math.min(5, (int) scaledHue);
        float fraction = scaledHue - sector;
        float p = normalizedValue * (1.0F - normalizedSaturation);
        float q = normalizedValue * (1.0F - fraction * normalizedSaturation);
        float t = normalizedValue * (1.0F - (1.0F - fraction) * normalizedSaturation);

        float red;
        float green;
        float blue;
        switch (sector) {
            case 0 -> {
                red = normalizedValue;
                green = t;
                blue = p;
            }
            case 1 -> {
                red = q;
                green = normalizedValue;
                blue = p;
            }
            case 2 -> {
                red = p;
                green = normalizedValue;
                blue = t;
            }
            case 3 -> {
                red = p;
                green = q;
                blue = normalizedValue;
            }
            case 4 -> {
                red = t;
                green = p;
                blue = normalizedValue;
            }
            default -> {
                red = normalizedValue;
                green = p;
                blue = q;
            }
        }

        return Math.round(red * 255.0F) << 16
                | Math.round(green * 255.0F) << 8
                | Math.round(blue * 255.0F);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    enum DragTarget {
        NONE,
        PALETTE,
        HUE
    }

    record Hsv(float hue, float saturation, float value) {
    }
}
