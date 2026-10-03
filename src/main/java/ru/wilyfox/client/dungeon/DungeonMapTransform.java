package ru.wilyfox.client.dungeon;

import org.joml.Vector2f;
import ru.wilyfox.client.protocol.DwDungeonPosition;

/** Projects signed server coordinates into the cropped 128-pixel FH map viewport. */
public record DungeonMapTransform(float anchorX, float anchorY, float zoom, float rotation) {
    public static final int MAP_SIZE = 128;
    public static final int UV_OFFSET = 1;
    public static final int UV_SIZE = 126;

    public static boolean hasPosition(DwDungeonPosition position) {
        return position != null && !(position.x() == -1 && position.y() == -1);
    }

    public static DungeonMapTransform create(DwDungeonPosition position, boolean anchor, boolean rotate,
                                            int zoomPercent, float playerYaw) {
        // Older servers may send map data without a position packet. Keep their static map usable.
        if (!hasPosition(position)) {
            return new DungeonMapTransform(MAP_SIZE / 2f, MAP_SIZE / 2f, 1f, 0f);
        }
        return new DungeonMapTransform(
                anchor ? textureCoordinate(position.x()) : MAP_SIZE / 2f,
                anchor ? textureCoordinate(position.y()) : MAP_SIZE / 2f,
                Math.max(100, Math.min(310, zoomPercent)) / 100f,
                rotate ? (float) Math.toRadians(180f - playerYaw) : 0f);
    }

    public Vector2f project(DwDungeonPosition position) {
        float x = (textureCoordinate(position.x()) - anchorX) * zoom;
        float y = (textureCoordinate(position.y()) - anchorY) * zoom;
        float cosine = (float) Math.cos(rotation);
        float sine = (float) Math.sin(rotation);
        return new Vector2f(MAP_SIZE / 2f + x * cosine - y * sine,
                MAP_SIZE / 2f + x * sine + y * cosine);
    }

    public static float textureCoordinate(int coordinate) {
        // blit maps source [1,127] to destination [0,128]; compensate that one-pixel crop.
        return (coordinate - (float) UV_OFFSET) * MAP_SIZE / UV_SIZE;
    }
}
