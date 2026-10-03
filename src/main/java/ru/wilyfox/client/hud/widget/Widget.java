package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.layer.HudLayer;

public interface Widget {
    int getStartX();

    int getStartY();

    int getWidth();

    int getHeight();

    void setStartX(int x);

    void setStartY(int y);

    HudLayer getLayer();

    boolean isVisible();

    void render(GuiGraphicsExtractor context, DeltaTracker tickCounter);

    default boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= getStartX()
                && mouseX <= getStartX() + getWidth()
                && mouseY >= getStartY()
                && mouseY <= getStartY() + getHeight();
    }

    default int getLeft() { return getStartX(); }
    default int getRight() { return getStartX() + getWidth(); }
    default int getTop() { return getStartY(); }
    default int getBottom() { return getStartY() + getHeight(); }
    default int getCenterX() { return getStartX() + getWidth() / 2; }
    default int getCenterY() { return getStartY() + getHeight() / 2; }

    default int getCornerX(WidgetCorner corner) {
        return switch (corner) {
            case TOP_LEFT, BOTTOM_LEFT -> getLeft();
            case TOP_RIGHT, BOTTOM_RIGHT -> getRight();
        };
    }

    default int getCornerY(WidgetCorner corner) {
        return switch (corner) {
            case TOP_LEFT, TOP_RIGHT -> getTop();
            case BOTTOM_LEFT, BOTTOM_RIGHT -> getBottom();
        };
    }

    default String getDisplayName() {
        return getClass().getSimpleName();
    }
}
