package ru.wilyfox.client.hud.widget;

import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.config.ConfigManager;

public final class WidgetUtils {

    private WidgetUtils() {}

    /**
     * Single source of truth for the "UnClutter" toggle: when off, widgets skip drawing their
     * descriptive name/title header (and reclaim its layout space). Applies in both gameplay and
     * the HUD editor so the two stay visually consistent. Panel background/accent line are kept.
     */
    public static boolean showWidgetTitles() {
        return !ConfigManager.get().render.unclutterWidgets;
    }

    public static void drawCorners(GuiGraphics context, int x, int y, int width, int height, int color) {
        int cornerLength = 4;
        int thickness = 1;

        drawTopLeftCorner(context, x, y, width, height, color);
        drawTopRightCorner(context, x, y, width, height, color);
        drawBottomLeftCorner(context, x, y, width, height, color);
        drawBottomRightCorner(context, x, y, width, height, color);
    }

    public static void drawTopLeftCorner(GuiGraphics context, int x, int y, int width, int height, int color) {
        int cornerLength = 4;
        int thickness = 1;

        context.fill(x, y, x + cornerLength, y + thickness, color);
        context.fill(x, y, x + thickness, y + cornerLength, color);
    }

    public static void drawTopRightCorner(GuiGraphics context, int x, int y, int width, int height, int color) {
        int cornerLength = 4;
        int thickness = 1;

        context.fill(x + width - cornerLength, y, x + width, y + thickness, color);
        context.fill(x + width - thickness, y, x + width, y + cornerLength, color);
    }

    public static void drawBottomLeftCorner(GuiGraphics context, int x, int y, int width, int height, int color) {
        int cornerLength = 4;
        int thickness = 1;

        context.fill(x, y + height - thickness, x + cornerLength, y + height, color);
        context.fill(x, y + height - cornerLength, x + thickness, y + height, color);
    }

    public static void drawBottomRightCorner(GuiGraphics context, int x, int y, int width, int height, int color) {
        int cornerLength = 4;
        int thickness = 1;

        context.fill(x + width - cornerLength, y + height - thickness, x + width, y + height, color);
        context.fill(x + width - thickness, y + height - cornerLength, x + width, y + height, color);
    }

    public static void drawAnchorPoint(GuiGraphics context, int centerX, int centerY, int color) {
        drawAnchorPoint(context, centerX, centerY, color, 10);
    }

    public static void drawAnchorPoint(GuiGraphics context, int centerX, int centerY, int color, int size) {
        int half = size / 2;
        drawCorners(context, centerX - half, centerY - half, size, size, color);
    }

    public static void drawCornerMarker(GuiGraphics context, int x, int y, int color) {
        int size = 4;
        drawCorners(context, x, y, size, size, color);
    }

    public static void drawSnapLine(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            context.fill(x1, minY, x1 + 1, maxY + 1, color);
            return;
        }

        if (y1 == y2) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            context.fill(minX, y1, maxX + 1, y1 + 1, color);
        }
    }
}