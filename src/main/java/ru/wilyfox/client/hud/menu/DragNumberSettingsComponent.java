package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.audio.UiSounds;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.utils.InputModifiers;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class DragNumberSettingsComponent extends SettingsComponent {
    private final IntSupplier getter;
    private final IntConsumer setter;
    private final int min;
    private final IntSupplier max;
    private final int step;
    private final NumberDrag drag = new NumberDrag();
    private boolean dragging;
    private boolean dirty;
    private boolean labelTruncated;
    private java.util.function.IntFunction<String> formatter = Integer::toString;

    public DragNumberSettingsComponent withFormatter(java.util.function.IntFunction<String> formatter) {
        this.formatter = java.util.Objects.requireNonNull(formatter); return this;
    }

    public DragNumberSettingsComponent(int x, int y, int width, int height, String label,
                                       IntSupplier getter, IntConsumer setter, int min, int max) {
        this(x, y, width, height, label, getter, setter, min, max, 1);
    }

    public DragNumberSettingsComponent(int x, int y, int width, int height, String label,
                                       IntSupplier getter, IntConsumer setter, int min, int max, int step) {
        this(x, y, width, height, label, getter, setter, min, () -> max, step);
    }

    public DragNumberSettingsComponent(int x, int y, int width, int height, String label,
                                       IntSupplier getter, IntConsumer setter, int min, IntSupplier max, int step) {
        super(x, y, width, height, label);
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
    }

    public boolean isDragging() { return dragging; }

    private int valueWidth() { return Math.min(64, Math.max(0, width - 16)); }
    private int valueX() { return x + width - 8 - valueWidth(); }
    private boolean overValue(double mouseX, double mouseY) {
        return valueWidth() > 0 && mouseX >= valueX() && mouseX <= valueX() + valueWidth()
                && mouseY >= y + 3 && mouseY <= y + height - 3;
    }

    @Override public void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        boolean hovered = isHovered(mouseX, mouseY);
        boolean valueHovered = overValue(mouseX, mouseY);
        HudSurface.fillRounded(context, x, y, width, height, 4,
                hovered || dragging ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT);
        int textY = y + (height - font.lineHeight) / 2;
        int labelWidth = Math.max(0, valueX() - x - 14);
        labelTruncated = font.width(label) > labelWidth;
        String shown = labelTruncated
                ? font.plainSubstrByWidth(label, Math.max(0, labelWidth - font.width("…"))) + "…" : label;
        context.text(font, shown, x + 8, textY, hovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_PRIMARY);
        HudSurface.fillRounded(context, valueX(), y + 3, valueWidth(), Math.max(0, height - 6), 3,
                valueHovered || dragging ? WidgetTheme.PANEL_BG : WidgetTheme.BAR_BG);
        if (valueHovered || dragging) context.fill(valueX() + 3, y + height - 4,
                valueX() + valueWidth() - 3, y + height - 3, WidgetTheme.ACCENT_LINE);
        context.centeredText(font, formatter.apply(getter.getAsInt()), valueX() + valueWidth() / 2, textY,
                dragging ? WidgetTheme.TITLE : WidgetTheme.TEXT_SOFT);
    }

    @Override public String getTooltip(int mouseX, int mouseY) {
        if (overValue(mouseX, mouseY) && !dragging) return "Drag left/right. Shift: fine adjustment";
        return labelTruncated && isHovered(mouseX, mouseY) ? label : null;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !overValue(mouseX, mouseY)) return false;
        dragging = true;
        drag.begin(mouseX);
        return true;
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging || button != 0) return false;
        updateValue(mouseX);
        return true;
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!dragging || button != 0) return false;
        updateValue(mouseX);
        onClickOutside();
        return true;
    }

    private void updateValue(double mouseX) {
        int previous = getter.getAsInt();
        int value = drag.update(mouseX, previous, min, max.getAsInt(), step, InputModifiers.hasShiftDown());
        if (value == previous) return;
        setter.accept(value);
        if (getter.getAsInt() != previous) {
            dirty = true;
            UiSounds.scroll();
        }
    }

    @Override public void onClickOutside() {
        if (dragging) UiSounds.cancelScroll();
        dragging = false;
        if (!dirty) return;
        dirty = false;
        ConfigManager.save();
    }
}
