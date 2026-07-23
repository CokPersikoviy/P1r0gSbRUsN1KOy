package ru.wilyfox.client.hud.menu;

import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BooleanSupplier;

public abstract class SettingsComponent implements MenuElement {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected String label;
    protected int preferredHeight = 22;
    protected int indent = 0;
    protected BooleanSupplier visibleWhen = () -> true;

    protected SettingsComponent(int x, int y, int width, int height, String label) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getPreferredHeight() {
        return preferredHeight;
    }

    public int getIndent() {
        return indent;
    }

    public SettingsComponent withPreferredHeight(int preferredHeight) {
        this.preferredHeight = Math.max(8, preferredHeight);
        return this;
    }

    public SettingsComponent withIndent(int indent) {
        this.indent = Math.max(0, indent);
        return this;
    }

    public SettingsComponent withVisibility(BooleanSupplier visibleWhen) {
        this.visibleWhen = visibleWhen != null ? visibleWhen : () -> true;
        return this;
    }

    public boolean isVisible() {
        return visibleWhen.getAsBoolean();
    }

    /** Full-text tooltip to show when hovered (e.g. a label truncated to fit). Null = none. */
    public String getTooltip(int mouseX, int mouseY) {
        return null;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    public void onClickOutside() {
    }

    @Override
    public abstract void render(GuiGraphics context, int mouseX, int mouseY);

    @Override
    public abstract boolean mouseClicked(double mouseX, double mouseY, int button);
}
