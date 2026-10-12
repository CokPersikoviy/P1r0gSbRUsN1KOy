package ru.wilyfox.client.hud.snap;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static ru.wilyfox.client.hud.snap.SnapSession.*;

/** Sweeps a translated selection along each axis, leaving the other axis free to slide. */
final class DragCollisionLimiter {
    private static final double EPSILON = .001;
    record Position(double x, double y) {}
    private record Pair(int member, String target) {}
    private final Rect origin;
    private final List<Rect> members;
    private final List<Target> obstacles;
    private final Set<Pair> existingOverlaps = new HashSet<>();
    private double x, y;

    DragCollisionLimiter(Rect root, Collection<Rect> members, List<Target> targets) {
        origin = root; this.members = List.copyOf(members);
        obstacles = targets.stream().filter(target -> !target.id().startsWith("@")).toList();
        bypass(root.x(), root.y());
    }

    Position limit(double requestedX, double requestedY, double gap) {
        releaseExistingOverlaps();
        double nextX = x, nextY = y;
        // Process the dominant motion first so sliding behaves the same for horizontal/vertical drags.
        if (Math.abs(requestedX - x) >= Math.abs(requestedY - y)) {
            nextX += sweep(Axis.X, requestedX - x, nextX, nextY, gap);
            nextY += sweep(Axis.Y, requestedY - y, nextX, nextY, gap);
        } else {
            nextY += sweep(Axis.Y, requestedY - y, nextX, nextY, gap);
            nextX += sweep(Axis.X, requestedX - x, nextX, nextY, gap);
        }
        return new Position(nextX, nextY);
    }

    private double sweep(Axis axis, double delta, double startX, double startY, double gap) {
        double allowed = delta;
        for (int index = 0; index < members.size(); index++) {
            Rect member = members.get(index);
            Rect body = member.move(startX - origin.x(), startY - origin.y());
            for (Target target : obstacles) {
                if (!existingOverlaps.isEmpty() && existingOverlaps.contains(new Pair(index, target.id()))) {
                    Rect neighbor = target.bounds();
                    if (!intersects(body, neighbor)) continue;
                    boolean deeper = delta > 0 && body.point(axis, Edge.START) < neighbor.point(axis, Edge.START)
                            && body.point(axis, Edge.END) < neighbor.point(axis, Edge.END)
                            || delta < 0 && body.point(axis, Edge.START) > neighbor.point(axis, Edge.START)
                            && body.point(axis, Edge.END) > neighbor.point(axis, Edge.END);
                    if (deeper) allowed = 0;
                    continue;
                }
                Rect obstacle = expanded(target.bounds(), gap);
                // A previously closer-than-gap placement may leave without being pushed out.
                // It still collides with the actual body of the neighbor.
                if (intersects(body, obstacle)) obstacle = target.bounds();
                if (!orthogonalOverlap(body, obstacle, axis)) continue;
                if (delta > 0 && body.point(axis, Edge.END) <= obstacle.point(axis, Edge.START) + EPSILON) {
                    allowed = Math.min(allowed, Math.max(0, obstacle.point(axis, Edge.START) - body.point(axis, Edge.END)));
                } else if (delta < 0 && body.point(axis, Edge.START) >= obstacle.point(axis, Edge.END) - EPSILON) {
                    allowed = Math.max(allowed, Math.min(0, obstacle.point(axis, Edge.END) - body.point(axis, Edge.START)));
                }
            }
        }
        return allowed;
    }

    boolean canPlace(double candidateX, double candidateY) {
        for (int index = 0; index < members.size(); index++) {
            Rect body = members.get(index).move(candidateX - origin.x(), candidateY - origin.y());
            for (Target target : obstacles) {
                if (!intersects(body, target.bounds())) continue;
                if (!existingOverlaps.contains(new Pair(index, target.id()))) return false;
                Rect previous = members.get(index).move(x - origin.x(), y - origin.y());
                if (intersectionArea(body, target.bounds()) > intersectionArea(previous, target.bounds()) + EPSILON) return false;
            }
        }
        return true;
    }

    void accept(double acceptedX, double acceptedY) {
        x = acceptedX; y = acceptedY; releaseExistingOverlaps();
    }

    void bypass(double requestedX, double requestedY) {
        x = requestedX; y = requestedY;
        // Intentional Ctrl overlaps and legacy placements can be separated without a far-edge jump.
        for (int index = 0; index < members.size(); index++) for (Target target : obstacles)
            if (overlapsAt(index, target, x, y)) existingOverlaps.add(new Pair(index, target.id()));
        releaseExistingOverlaps();
    }

    private void releaseExistingOverlaps() {
        if (existingOverlaps.isEmpty()) return;
        for (int index = 0; index < members.size(); index++) for (Target target : obstacles)
            if (!overlapsAt(index, target, x, y)) existingOverlaps.remove(new Pair(index, target.id()));
    }

    private boolean overlapsAt(int index, Target target, double rootX, double rootY) {
        return intersects(members.get(index).move(rootX - origin.x(), rootY - origin.y()), target.bounds());
    }

    private static Rect expanded(Rect rect, double gap) {
        return new Rect(rect.x() - gap, rect.y() - gap, rect.width() + 2 * gap, rect.height() + 2 * gap);
    }
    private static boolean intersects(Rect a, Rect b) {
        return a.x() < b.right() - EPSILON && a.right() > b.x() + EPSILON
                && a.y() < b.bottom() - EPSILON && a.bottom() > b.y() + EPSILON;
    }
    private static double intersectionArea(Rect a, Rect b) {
        return Math.max(0, Math.min(a.right(), b.right()) - Math.max(a.x(), b.x()))
                * Math.max(0, Math.min(a.bottom(), b.bottom()) - Math.max(a.y(), b.y()));
    }
    private static boolean orthogonalOverlap(Rect a, Rect b, Axis axis) {
        return axis == Axis.X ? a.y() < b.bottom() - EPSILON && a.bottom() > b.y() + EPSILON
                : a.x() < b.right() - EPSILON && a.right() > b.x() + EPSILON;
    }
}
