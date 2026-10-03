package ru.wilyfox.client.hud.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface MenuElement {
    void render(GuiGraphicsExtractor context, int mouseX, int mouseY);
    boolean mouseClicked(double mouseX, double mouseY, int button);
    boolean mouseReleased(double mouseX, double mouseY, int button);
    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY);
    boolean keyPressed(int keyCode, int scanCode, int modifiers);
    boolean charTyped(int codePoint, int modifiers);

    int getX();
    int getY();
    int getWidth();
    int getHeight();

    default boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX <= getX() + getWidth()
                && mouseY >= getY() && mouseY <= getY() + getHeight();
    }
}
