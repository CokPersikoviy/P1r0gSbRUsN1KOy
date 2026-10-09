package ru.wilyfox.client.hud.internal;

import java.util.List;

/** A bounded search over a snapshot of widget bounds; it never asks live widgets for their sizes. */
public final class WidgetPlacement {
    private static final int MAX_CANDIDATES = 512;
    private static final int GAP = 6;

    private WidgetPlacement() {}

    public static Position find(int screenWidth, int screenHeight, int canvasLeft,
                                int width, int height, List<Bounds> occupied) {
        int maxX = Math.max(0, screenWidth - width), maxY = Math.max(0, screenHeight - height);
        int left = Math.max(0, Math.min(canvasLeft, maxX));
        Position center = new Position(left + (maxX - left) / 2, maxY / 2);
        if (!overlaps(center.x(), center.y(), width, height, occupied)) return center;
        // Oversized widgets stay at their visible origin, rather than searching a nonexistent area.
        if (width > screenWidth || height > screenHeight) return center;
        int step = Math.max(12, (int) Math.ceil(Math.sqrt((double) (maxX - left + 1) * (maxY + 1) / MAX_CANDIDATES)));
        int checked = 0;
        for (int y = Math.min(8, maxY); y <= maxY; y += step) {
            for (int x = left; x <= maxX; x += step) {
                if (++checked > MAX_CANDIDATES) return center;
                if (!overlaps(x, y, width, height, occupied)) return new Position(x, y);
            }
        }
        // A full canvas is allowed to overlap; no exhaustive search or off-screen placement.
        return center;
    }

    private static boolean overlaps(int x, int y, int width, int height, List<Bounds> occupied) {
        for (Bounds bounds : occupied) {
            if (x < bounds.x() + bounds.width() + GAP && x + width + GAP > bounds.x()
                    && y < bounds.y() + bounds.height() + GAP && y + height + GAP > bounds.y()) return true;
        }
        return false;
    }

    public record Bounds(int x, int y, int width, int height) {}
    public record Position(int x, int y) {}
}
