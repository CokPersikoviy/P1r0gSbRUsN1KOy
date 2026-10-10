package ru.wilyfox.client.visuals;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.VisualsConfig;
import ru.wilyfox.client.profiler.ModProfiler;

/** One target only. Shape geometry is cached locally, never per loaded block. */
public final class VisualBlockOutline {
    private static Object level;
    private static AABB from, to;
    private static VoxelShape shape;
    private static Vec3 origin;
    private static VoxelShape cachedShape;
    private static final double[] EMPTY_EDGES = new double[0];
    private static double[] edges = EMPTY_EDGES;
    private static java.util.List<Face> faces = java.util.List.of();
    private static long movedAt, appearedAt, lastSeen;
    private VisualBlockOutline() {}
    public static void reset() { if (level == null && shape == null) return; level = null; from = to = null; shape = null; origin = null; cachedShape = null; edges = EMPTY_EDGES; faces = java.util.List.of(); }
    public static void extract(BlockOutlineRenderState state, boolean render, Camera camera) {
        var c = ConfigManager.get().visuals; var mc = Minecraft.getInstance();
        if (!c.advancedOutline() || mc.level == null || mc.gui.hud.isHidden()) { reset(); return; }
        try (var profile = ModProfiler.getInstance().scope("visuals/blockOutline")) {
            long now = System.nanoTime() / 1_000_000;
            if (level != mc.level) { reset(); level = mc.level; }
            boolean target = render && state != null && !state.shape().isEmpty();
            if (target) {
                Vec3 pos = Vec3.atLowerCornerOf(state.pos());
                AABB bounds = state.shape().bounds().move(pos);
                if (to == null) { from = to = bounds; appearedAt = now; movedAt = now - c.blockAnimation; }
                else if (!to.equals(bounds)) { from = current(now, c.blockAnimation); to = bounds; movedAt = now; }
                shape = state.shape(); origin = pos; lastSeen = now;
                if (shape != cachedShape) {
                    var raw = new it.unimi.dsi.fastutil.doubles.DoubleArrayList();
                    shape.forAllEdges((x, y, z, a, b, d) -> { raw.add(x); raw.add(y); raw.add(z); raw.add(a); raw.add(b); raw.add(d); });
                    edges = raw.toDoubleArray(); faces = exteriorFaces(shape.toAabbs()); cachedShape = shape;
                }
            } else if (to == null || now - lastSeen > c.blockLinger + c.blockFade) { reset(); return; }
            double alpha = c.blockFade == 0 ? 1 : Math.min(1, (now - appearedAt) / (double) c.blockFade);
            if (!target && now - lastSeen > c.blockLinger) alpha *= c.blockFade == 0 ? 0 : 1 - Math.clamp((now - lastSeen - c.blockLinger) / (double) c.blockFade, 0, 1);
            if (alpha <= 0) return;
            AABB box = current(now, c.blockAnimation), source = shape.bounds().move(origin);
            int color = Visuals.alpha(stroke(now, c), alpha), fill = Visuals.alpha(c.blockFillColor, alpha);
            try (var collector = mc.levelExtractor.collectPerFrameMainThreadGizmos()) {
                if (c.blockMode == VisualsConfig.OutlineMode.BOX) {
                    if (c.blockFill) VisualWorldLines.top(Gizmos.cuboid(box.inflate(0.001), GizmoStyle.fill(fill)), c.blockThroughWalls);
                    VisualWorldLines.box(box.inflate(0.001), c.blockStyle, color, (float) c.blockOutlineWidth, c.blockThroughWalls);
                } else {
                    if (c.blockFill) for (Face face : faces) {
                        AABB translated = face.box().move(origin);
                        AABB placed = new AABB(place(translated.minX, source.minX, source.maxX, box.minX, box.maxX),
                                place(translated.minY, source.minY, source.maxY, box.minY, box.maxY), place(translated.minZ, source.minZ, source.maxZ, box.minZ, box.maxZ),
                                place(translated.maxX, source.minX, source.maxX, box.minX, box.maxX), place(translated.maxY, source.minY, source.maxY, box.minY, box.maxY),
                                place(translated.maxZ, source.minZ, source.maxZ, box.minZ, box.maxZ));
                        placed = placed.inflate(0.001);
                        VisualWorldLines.top(Gizmos.rect(new Vec3(placed.minX, placed.minY, placed.minZ),
                                new Vec3(placed.maxX, placed.maxY, placed.maxZ), face.direction(), GizmoStyle.fill(fill)), c.blockThroughWalls);
                    }
                    for (int i = 0; i + 5 < edges.length; i += 6) {
                        double x = edges[i], y = edges[i + 1], z = edges[i + 2], a = edges[i + 3], b = edges[i + 4], d = edges[i + 5];
                        VisualWorldLines.line(
                            place(x + origin.x, source.minX, source.maxX, box.minX, box.maxX), place(y + origin.y, source.minY, source.maxY, box.minY, box.maxY),
                            place(z + origin.z, source.minZ, source.maxZ, box.minZ, box.maxZ), place(a + origin.x, source.minX, source.maxX, box.minX, box.maxX),
                            place(b + origin.y, source.minY, source.maxY, box.minY, box.maxY), place(d + origin.z, source.minZ, source.maxZ, box.minZ, box.maxZ),
                            c.blockStyle, color, (float) c.blockOutlineWidth, c.blockThroughWalls);
                    }
                }
            }
        }
    }
    record Face(AABB box, Direction direction) {}
    /** Reference's face-center probe removes internal surfaces; evaluated only on shape changes. */
    static java.util.List<Face> exteriorFaces(java.util.List<AABB> boxes) {
        var result = new java.util.ArrayList<Face>();
        for (AABB box : boxes) for (Direction direction : Direction.values()) {
            Vec3 center = box.getCenter();
            double x = center.x, y = center.y, z = center.z;
            switch (direction) {
                case WEST -> x = box.minX - 1e-4;
                case EAST -> x = box.maxX + 1e-4;
                case DOWN -> y = box.minY - 1e-4;
                case UP -> y = box.maxY + 1e-4;
                case NORTH -> z = box.minZ - 1e-4;
                case SOUTH -> z = box.maxZ + 1e-4;
            }
            boolean inside = false;
            for (AABB other : boxes) if (other.contains(x, y, z)) { inside = true; break; }
            if (!inside) result.add(new Face(box, direction));
        }
        return java.util.List.copyOf(result);
    }
    static double place(double value, double min, double max, double currentMin, double currentMax) {
        return max - min <= 1e-9 ? currentMin : currentMin + (value - min) / (max - min) * (currentMax - currentMin);
    }
    private static AABB current(long now, int duration) {
        double t = duration <= 0 ? 1 : Math.clamp((now - movedAt) / (double) duration, 0, 1); t = 1 - Math.pow(1 - t, 3);
        return t >= 1 ? to : new AABB(lerp(from.minX, to.minX, t), lerp(from.minY, to.minY, t), lerp(from.minZ, to.minZ, t),
                lerp(from.maxX, to.maxX, t), lerp(from.maxY, to.maxY, t), lerp(from.maxZ, to.maxZ, t));
    }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static int stroke(long now, VisualsConfig c) {
        if (!c.blockRainbow) return c.blockOutlineColor;
        float hue = (now % (12000 / c.blockRainbowSpeed)) / (12000f / c.blockRainbowSpeed);
        return c.blockOutlineColor & 0xFF000000 | net.minecraft.util.Mth.hsvToRgb(hue, 0.65f, 1) & 0xFFFFFF;
    }
}
