package ru.wilyfox.client.hud.internal;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import ru.wilyfox.client.hud.indicators.ScreenAnchor;
import ru.wilyfox.client.hud.widget.Widget;

public final class HudScreenAnchorHelper {
    private HudScreenAnchorHelper() {
    }

    public static int getAnchorSnapDistance(Widget widget, AnchorPlacement placement, boolean shiftHeld) {
        if (!shiftHeld) {
            return Math.abs(widget.getStartX() - placement.startX()) + Math.abs(widget.getStartY() - placement.startY());
        }

        return switch (placement.anchor()) {
            case TOP_CENTER, BOTTOM_CENTER, CENTER -> Math.abs(widget.getCenterX() - (placement.startX() + widget.getWidth() / 2))
                    + Math.abs(widget.getTop() - placement.startY());
            case LEFT_CENTER -> Math.abs(widget.getLeft() - placement.startX())
                    + Math.abs(widget.getCenterY() - (placement.startY() + widget.getHeight() / 2));
            case RIGHT_CENTER -> Math.abs(widget.getRight() - (placement.startX() + widget.getWidth()))
                    + Math.abs(widget.getCenterY() - (placement.startY() + widget.getHeight() / 2));
            default -> Math.abs(widget.getStartX() - placement.startX()) + Math.abs(widget.getStartY() - placement.startY());
        };
    }

    public static AnchorPlacement getAnchorPlacement(
            ScreenAnchor anchor,
            Widget widget,
            int screenWidth,
            int screenHeight,
            int screenSnapMargin,
            int hotbarWidth,
            int hotbarHeight,
            int hotbarAnchorGap,
            int offhandSlotWidth
    ) {
        int hotbarLeft = getHotbarLeft(screenWidth, hotbarWidth);
        int hotbarTop = getHotbarTop(screenHeight, hotbarHeight);
        int leftInset = getHotbarSideInset(true, offhandSlotWidth);
        int rightInset = getHotbarSideInset(false, offhandSlotWidth);

        int anchoredX = switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> screenSnapMargin;
            case LEFT_CENTER -> 0;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - widget.getWidth() - screenSnapMargin;
            case RIGHT_CENTER -> screenWidth - widget.getWidth();
            case TOP_CENTER, BOTTOM_CENTER, CENTER -> screenWidth / 2 - widget.getWidth() / 2;
            case HOTBAR_LEFT -> hotbarLeft - widget.getWidth() - hotbarAnchorGap - leftInset;
            case HOTBAR_RIGHT -> hotbarLeft + hotbarWidth + hotbarAnchorGap + rightInset;
        };

        int anchoredY = switch (anchor) {
            case TOP_LEFT, TOP_RIGHT -> screenSnapMargin;
            case TOP_CENTER -> 0;
            case BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM_CENTER -> screenHeight - widget.getHeight() - screenSnapMargin;
            case LEFT_CENTER, RIGHT_CENTER, CENTER -> screenHeight / 2 - widget.getHeight() / 2;
            case HOTBAR_LEFT, HOTBAR_RIGHT -> hotbarTop + (hotbarHeight - widget.getHeight()) / 2;
        };

        anchoredX = clamp(anchoredX, 0, Math.max(0, screenWidth - widget.getWidth()));
        anchoredY = clamp(anchoredY, 0, Math.max(0, screenHeight - widget.getHeight()));

        return new AnchorPlacement(anchor, anchoredX, anchoredY, 0);
    }

    public static int getHotbarLeftAnchorX(int screenWidth, int hotbarWidth, int hotbarAnchorGap, int offhandSlotWidth) {
        return getHotbarLeft(screenWidth, hotbarWidth) - hotbarAnchorGap - getHotbarSideInset(true, offhandSlotWidth);
    }

    public static int getHotbarRightAnchorX(int screenWidth, int hotbarWidth, int hotbarAnchorGap, int offhandSlotWidth) {
        return getHotbarLeft(screenWidth, hotbarWidth) + hotbarWidth + hotbarAnchorGap + getHotbarSideInset(false, offhandSlotWidth);
    }

    public static int getHotbarAnchorY(int screenHeight, int hotbarHeight) {
        return getHotbarTop(screenHeight, hotbarHeight) + hotbarHeight / 2;
    }

    private static int getHotbarLeft(int screenWidth, int hotbarWidth) {
        return (screenWidth - hotbarWidth) / 2;
    }

    private static int getHotbarTop(int screenHeight, int hotbarHeight) {
        return screenHeight - hotbarHeight;
    }

    private static int getHotbarSideInset(boolean leftSide, int offhandSlotWidth) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.player.getOffhandItem().isEmpty()) {
            return 0;
        }

        boolean offhandOnLeft = mc.player.getMainArm() == HumanoidArm.RIGHT;
        return offhandOnLeft == leftSide ? offhandSlotWidth : 0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record AnchorPlacement(ScreenAnchor anchor, int startX, int startY, int distance) {
        public AnchorPlacement withDistance(int value) {
            return new AnchorPlacement(anchor, startX, startY, value);
        }
    }
}
