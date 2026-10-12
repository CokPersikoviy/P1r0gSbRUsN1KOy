package ru.wilyfox.client.hud.snap;

import ru.wilyfox.client.hud.config.HudSnapConfig;
import java.util.*;

/** A gesture's geometry, independent of Minecraft, rendering and persistence. All units are GUI units. */
public final class SnapSession {
    public enum Axis { X, Y }
    public enum Edge { START, CENTER, END }
    public enum Kind { ALIGN, CENTER, DOCK, SCREEN, GAP }
    public record Rect(double x, double y, double width, double height) {
        public double right() { return x + width; }
        public double bottom() { return y + height; }
        public double centerX() { return x + width / 2; }
        public double centerY() { return y + height / 2; }
        public double point(Axis axis, Edge edge) {
            return (axis == Axis.X ? x : y) + (axis == Axis.X ? width : height) * switch (edge) { case START -> 0; case CENTER -> .5; case END -> 1; };
        }
        public Rect move(double dx, double dy) { return new Rect(x + dx, y + dy, width, height); }
    }
    public record Target(String id, Rect bounds) {}
    public record Candidate(Axis axis, Kind kind, String targetId, Edge own, Edge targetEdge,
                            double delta, double line, double from, double to, double gap) {
        String key() { return axis + ":" + kind + ":" + targetId + ":" + own + ":" + targetEdge; }
    }
    public record Result(double x, double y, Rect bounds, Candidate horizontal, Candidate vertical) {}
    private record Gap(Axis axis, Target first, Target second, double size) {}
    private final Rect root, group;
    private final List<Target> targets;
    private final List<Gap> gaps;
    private final DragCollisionLimiter collisions;
    private Candidate heldX, heldY;
    private Result result;

    public SnapSession(Rect root, Rect group, Collection<Target> targets, Set<String> movingIds) {
        this(root, group, targets, movingIds, List.of(group));
    }
    public SnapSession(Rect root, Rect group, Collection<Target> targets, Set<String> movingIds, Collection<Rect> movingMembers) {
        this.root = root; this.group = group;
        this.targets = targets.stream().filter(target -> !movingIds.contains(target.id()))
                .sorted(Comparator.comparing(Target::id)).toList();
        collisions = new DragCollisionLimiter(root, movingMembers, this.targets);
        var pairs = new ArrayList<Gap>();
        for (int i = 0; i < this.targets.size(); i++) for (int j = i + 1; j < this.targets.size(); j++) {
            Target a = this.targets.get(i), b = this.targets.get(j);
            if (a.id().startsWith("@") || b.id().startsWith("@")) continue;
            for (Axis axis : Axis.values()) {
                if (!overlap(a.bounds(), b.bounds(), axis)) continue;
                Target first = a.bounds().point(axis, Edge.START) <= b.bounds().point(axis, Edge.START) ? a : b;
                Target second = first == a ? b : a;
                double size = second.bounds().point(axis, Edge.START) - first.bounds().point(axis, Edge.END);
                if (size > 0) pairs.add(new Gap(axis, first, second, size));
            }
        }
        pairs.sort(Comparator.comparingDouble(Gap::size).thenComparing(gap -> gap.first.id()).thenComparing(gap -> gap.second.id()));
        gaps = List.copyOf(pairs.subList(0, Math.min(64, pairs.size())));
    }
    public Result solve(double rawX, double rawY, double screenWidth, double screenHeight, double guiScale,
                        HudSnapConfig settings, boolean bypass) {
        if (group.width() > screenWidth) rawX = Math.max(0, Math.min(rawX, Math.max(0, screenWidth - root.width())));
        if (group.height() > screenHeight) rawY = Math.max(0, Math.min(rawY, Math.max(0, screenHeight - root.height())));
        Rect moving = group.move(rawX - root.x(), rawY - root.y());
        double dx = fit(moving.x(), moving.width(), screenWidth), dy = fit(moving.y(), moving.height(), screenHeight);
        moving = moving.move(dx, dy); rawX += dx; rawY += dy;
        if (bypass) {
            collisions.bypass(rawX, rawY);
            heldX = heldY = null;
            return result = new Result(rawX, rawY, moving, null, null);
        }
        var permitted = collisions.limit(rawX, rawY, settings.enabled ? settings.gap : 0);
        moving = moving.move(permitted.x() - rawX, permitted.y() - rawY);
        rawX = permitted.x(); rawY = permitted.y();
        if (!settings.enabled) {
            heldX = heldY = null;
            collisions.accept(rawX, rawY);
            return result = new Result(rawX, rawY, moving, null, null);
        }
        double scale = Double.isFinite(guiScale) && guiScale > 0 ? guiScale : 1;
        double capture = settings.capturePixels / scale, release = settings.releasePixels / scale;
        heldX = choose(Axis.X, moving, heldX, capture, release, screenWidth, screenHeight, settings);
        heldY = choose(Axis.Y, moving, heldY, capture, release, screenWidth, screenHeight, settings);
        double x = heldX == null ? 0 : heldX.delta(), y = heldY == null ? 0 : heldY.delta();
        Rect placed = moving.move(x, y);
        // A guide is accepted only if the resulting whole selection remains reachable.
        if (Math.abs(fit(placed.x(), placed.width(), screenWidth)) > .001) { heldX = null; x = 0; }
        if (Math.abs(fit(placed.y(), placed.height(), screenHeight)) > .001) { heldY = null; y = 0; }
        // Alignment itself must not push a valid placement inside another widget.
        if (!collisions.canPlace(rawX + x, rawY + y)) {
            boolean safeX = collisions.canPlace(rawX + x, rawY), safeY = collisions.canPlace(rawX, rawY + y);
            if (safeX && (!safeY || Math.abs(x) <= Math.abs(y))) { heldY = null; y = 0; }
            else if (safeY) { heldX = null; x = 0; }
            else { heldX = heldY = null; x = y = 0; }
        }
        collisions.accept(rawX + x, rawY + y);
        return result = new Result(rawX + x, rawY + y, moving.move(x, y), heldX, heldY);
    }
    private Candidate choose(Axis axis, Rect moving, Candidate held, double capture, double release,
                             double width, double height, HudSnapConfig config) {
        var selection = new Selection(held, capture, release);
        for (Target target : targets) {
            if (target.id().startsWith("@") && !config.screenTargets) continue;
            if (config.edgeAlignment && !target.id().startsWith("@")) {
                offer(selection, axis, Kind.ALIGN, target, moving, Edge.START, Edge.START, 0);
                offer(selection, axis, Kind.ALIGN, target, moving, Edge.END, Edge.END, 0);
            }
            if (config.centerAlignment) offer(selection, axis, Kind.CENTER, target, moving, Edge.CENTER, Edge.CENTER, 0);
            if (overlap(moving, target.bounds(), axis)) {
                offer(selection, axis, Kind.DOCK, target, moving, Edge.START, Edge.END, config.gap);
                offer(selection, axis, Kind.DOCK, target, moving, Edge.END, Edge.START, -config.gap);
            }
        }
        if (config.screenTargets) {
            var screen = new Target(axis == Axis.X ? "@screen-x" : "@screen-y", new Rect(8, 8, Math.max(0, width - 16), Math.max(0, height - 16)));
            offer(selection, axis, Kind.SCREEN, screen, moving, Edge.START, Edge.START, 0);
            offer(selection, axis, Kind.SCREEN, screen, moving, Edge.END, Edge.END, 0);
            if (config.centerAlignment) offer(selection, axis, Kind.SCREEN,
                    new Target(screen.id(), new Rect(0, 0, width, height)), moving, Edge.CENTER, Edge.CENTER, 0);
        }
        if (config.equalSpacing) for (Gap gap : gaps) {
            if (gap.axis != axis || !overlap(moving, gap.first.bounds(), axis) || !overlap(moving, gap.second.bounds(), axis)) continue;
            double size = axis == Axis.X ? moving.width() : moving.height();
            double start = gap.first.bounds().point(axis, Edge.END), end = gap.second.bounds().point(axis, Edge.START);
            if (gap.size >= size) gapOffer(selection, axis, moving, gap, start + (gap.size - size) / 2, "middle");
            gapOffer(selection, axis, moving, gap, gap.first.bounds().point(axis, Edge.START) - gap.size - size, "before");
            gapOffer(selection, axis, moving, gap, gap.second.bounds().point(axis, Edge.END) + gap.size, "after");
        }
        return selection.retained != null ? selection.retained : selection.best;
    }
    private static void offer(Selection selection, Axis axis, Kind kind, Target target, Rect moving, Edge own, Edge other, double gap) {
        double line = target.bounds().point(axis, other), delta = line + gap - moving.point(axis, own);
        if (Math.abs(delta) > selection.release) return;
        double from = axis == Axis.X ? Math.min(moving.y(), target.bounds().y()) : Math.min(moving.x(), target.bounds().x());
        double to = axis == Axis.X ? Math.max(moving.bottom(), target.bounds().bottom()) : Math.max(moving.right(), target.bounds().right());
        selection.offer(new Candidate(axis, kind, target.id(), own, other, delta, line, from, to, Math.abs(gap)));
    }
    private static void gapOffer(Selection selection, Axis axis, Rect moving, Gap gap, double position, String suffix) {
        double delta = position - moving.point(axis, Edge.START);
        if (Math.abs(delta) > selection.release) return;
        double from = axis == Axis.X ? Math.min(moving.y(), gap.first.bounds().y()) : Math.min(moving.x(), gap.first.bounds().x());
        double to = axis == Axis.X ? Math.max(moving.bottom(), gap.second.bounds().bottom()) : Math.max(moving.right(), gap.second.bounds().right());
        selection.offer(new Candidate(axis, Kind.GAP, gap.first.id() + ":" + gap.second.id() + ":" + suffix,
                Edge.START, Edge.START, delta, position, from, to,
                suffix.equals("middle") ? (gap.size - (axis == Axis.X ? moving.width() : moving.height())) / 2 : gap.size));
    }
    private static final class Selection {
        private final Candidate held;
        private final double capture, release;
        private Candidate best, retained;
        Selection(Candidate held, double capture, double release) { this.held = held; this.capture = capture; this.release = release; }
        void offer(Candidate candidate) {
            if (held != null && held.key().equals(candidate.key())) retained = candidate;
            if (Math.abs(candidate.delta) > capture) return;
            double distance = Math.abs(candidate.delta), bestDistance = best == null ? Double.POSITIVE_INFINITY : Math.abs(best.delta);
            if (best == null || distance < bestDistance - .001 || Math.abs(distance - bestDistance) <= .001
                    && (priority(candidate) < priority(best) || priority(candidate) == priority(best) && candidate.key().compareTo(best.key()) < 0)) best = candidate;
        }
        private static int priority(Candidate c) { return switch (c.kind) { case DOCK -> 0; case ALIGN -> 1; case CENTER -> 2; case SCREEN -> 3; case GAP -> 4; }; }
    }
    private static double fit(double start, double size, double screen) {
        if (size > screen) return 0;
        return Math.max(-start, Math.min(0, screen - start - size));
    }
    private static boolean overlap(Rect a, Rect b, Axis snapAxis) {
        return snapAxis == Axis.X ? a.y() < b.bottom() && a.bottom() > b.y() : a.x() < b.right() && a.right() > b.x();
    }
    public Result result() { return result; }
    /** Retain guide identity while refreshing a gesture's live target geometry. */
    public void inheritGuides(SnapSession previous) { heldX = previous.heldX; heldY = previous.heldY; }
    public List<Target> targets() { return targets; }
}
