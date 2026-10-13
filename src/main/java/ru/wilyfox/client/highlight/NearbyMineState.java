package ru.wilyfox.client.highlight;

import java.util.Collection;
import java.util.List;

/** Counts only detector results already cached on the client; never loads or scans a chunk. */
public final class NearbyMineState {
    public enum Kind { WORMS, BARRELS, CRYSTALS }
    public record Point(double x, double y, double z) {}
    public record Count(int count, double nearestDistance, Point nearest) {}
    private static List<Point> worms = List.of(), barrels = List.of(), crystals = List.of();
    private NearbyMineState() {}
    static void entities(Collection<Point> newWorms, Collection<Point> newCrystals) {
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("hud/NearbyMine/cache/entities")) {
            worms = List.copyOf(newWorms); crystals = newCrystals.stream().distinct().toList();
        }
    }
    static void barrels(Collection<Point> positions) {
 barrels = positions.stream().distinct().toList();     try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("hud/NearbyMine/cache/barrels")) {
 barrels = positions.stream().distinct().toList();     }
 barrels = positions.stream().distinct().toList(); }
    static void clearEntities() { worms = crystals = List.of(); }
    static void clearBarrels() { barrels = List.of(); }
    public static void clear() { clearEntities(); clearBarrels(); }
    public static Count count(Kind kind, Point player, int range) {
        var points = switch(kind) { case WORMS -> worms; case BARRELS -> barrels; case CRYSTALS -> crystals; };
        int count = 0; double nearestSq = Double.POSITIVE_INFINITY, radiusSq = Math.clamp(range,1,32) * (double)Math.clamp(range,1,32);
        Point nearest = null;
        for (var point : points) {
            double dx=point.x-player.x,dy=point.y-player.y,dz=point.z-player.z, distance=dx*dx+dy*dy+dz*dz;
            if (Math.abs(dy)>20 || distance>radiusSq) continue;
            count++; if (distance<nearestSq) { nearestSq=distance; nearest=point; }
        }
        return new Count(count,nearest==null?0:Math.sqrt(nearestSq),nearest);
    }
}
