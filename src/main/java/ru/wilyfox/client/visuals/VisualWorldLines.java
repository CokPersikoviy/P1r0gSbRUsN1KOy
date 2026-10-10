package ru.wilyfox.client.visuals;

import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.wilyfox.client.hud.config.VisualsConfig.LineStyle;

final class VisualWorldLines {
    private VisualWorldLines() {}
    static void box(AABB box, LineStyle style, int color, float width, boolean onTop) {
        for (int axis = 0; axis < 3; axis++) for (int u = 0; u < 2; u++) for (int v = 0; v < 2; v++) {
            double x = axis == 0 ? box.minX : (axis == 1 ? u : v) == 0 ? box.minX : box.maxX;
            double y = axis == 1 ? box.minY : (axis == 0 ? u : v) == 0 ? box.minY : box.maxY;
            double z = axis == 2 ? box.minZ : (axis == 0 ? v : u) == 0 ? box.minZ : box.maxZ;
            line(x, y, z, axis == 0 ? box.maxX : x, axis == 1 ? box.maxY : y, axis == 2 ? box.maxZ : z, style, color, width, onTop);
        }
    }
    static void line(double x, double y, double z, double a, double b, double c,
                     LineStyle style, int color, float width, boolean onTop) {
        if (color >>> 24 == 0) return;
        double dx = a - x, dy = b - y, dz = c - z;
        VisualLinePieces.forEach(style, Math.sqrt(dx * dx + dy * dy + dz * dz), (from, to) -> {
            Vec3 start = new Vec3(x + dx * from, y + dy * from, z + dz * from);
            Vec3 end = new Vec3(x + dx * to, y + dy * to, z + dz * to);
            if (style == LineStyle.NEON) {
                // Three bounded passes, using Minecraft's native pixel-width line renderer.
                top(Gizmos.line(start, end, Visuals.alpha(color, 0.10), width + 6), onTop);
                top(Gizmos.line(start, end, Visuals.alpha(color, 0.25), width + 3), onTop);
                top(Gizmos.line(start, end, Visuals.mix(color, color | 0xFFFFFF, 0.45), width), onTop);
            } else top(Gizmos.line(start, end, color, width), onTop);
        });
    }
    static void top(GizmoProperties gizmo, boolean onTop) { if (onTop) gizmo.setAlwaysOnTop(); }
}
