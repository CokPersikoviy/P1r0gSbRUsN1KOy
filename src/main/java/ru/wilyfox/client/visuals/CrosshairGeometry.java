package ru.wilyfox.client.visuals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import ru.wilyfox.client.hud.config.VisualsConfig;

/** Disjoint rectangles keep translucent outlines and inverted pixels from overdrawing. */
public final class CrosshairGeometry {
    public record Rect(int x, int y, int width, int height) {}
    public record Pixels(VisualsConfig.CrosshairStyle style, int length, int vertical, int thickness, int gap,
                         boolean top, boolean bottom, boolean left, boolean right, int dot, int outline) {}
    public record Shape(List<Rect> outline, List<Rect> lines, List<Rect> dot, int parity, int bottom) {}
    private Pixels last;
    private Shape cached;
    public Shape shape(Pixels p) {
        if (p.equals(last)) return cached;
        int t = p.thickness, lo = -t / 2, hi = lo + t, g = p.gap;
        List<Rect> raw = new ArrayList<>();
        switch (p.style) {
            case CROSS -> {
                if (p.right) raw.add(new Rect(hi + g, lo, p.length, t));
                if (p.left) raw.add(new Rect(lo - g - p.length, lo, p.length, t));
                if (p.bottom) raw.add(new Rect(lo, hi + g, t, p.vertical));
                if (p.top) raw.add(new Rect(lo, lo - g - p.vertical, t, p.vertical));
            }
            case X -> {
                for (int i = 0; i < p.length; i++) {
                    int d = g + i + 1;
                    raw.add(new Rect(lo + d, lo + d, t, t)); raw.add(new Rect(lo - d, lo + d, t, t));
                    raw.add(new Rect(lo + d, lo - d, t, t)); raw.add(new Rect(lo - d, lo - d, t, t));
                }
            }
            case CIRCLE -> ring(raw, t % 2 / 2.0, Math.max(0, g), Math.max(0, g) + t);
            case SQUARE -> {
                int a = lo - Math.max(0, g) - t, z = hi + Math.max(0, g) + t, inner = lo - Math.max(0, g), end = hi + Math.max(0, g);
                raw.add(new Rect(a, a, z - a, t)); raw.add(new Rect(a, end, z - a, t));
                raw.add(new Rect(a, inner, t, end - inner)); raw.add(new Rect(end, inner, t, end - inner));
            }
            case DOT -> {}
        }
        List<Rect> dot = p.dot > 0 ? List.of(new Rect(-p.dot / 2, -p.dot / 2, p.dot, p.dot)) : List.of();
        var all = new ArrayList<>(raw); all.addAll(dot);
        List<Rect> outline = List.of();
        if (p.outline > 0) {
            List<Rect> expanded = new ArrayList<>(all.size());
            for (Rect r : all) expanded.add(new Rect(r.x - p.outline, r.y - p.outline, r.width + 2 * p.outline, r.height + 2 * p.outline));
            outline = region(expanded, all);
        }
        var lines = region(raw, dot);
        int bottom = 0;
        for (Rect r : all) bottom = Math.max(bottom, r.y + r.height + p.outline);
        last = p; cached = new Shape(outline, lines, dot, (p.style == VisualsConfig.CrosshairStyle.DOT ? p.dot : t) % 2, bottom);
        return cached;
    }
    public static void ring(List<Rect> out, double center, int inner, int outer) {
        for (int y = (int) Math.floor(center - outer); y <= Math.ceil(center + outer); y++) {
            double dy = y + 0.5 - center;
            if (dy * dy > outer * outer) continue;
            double xo = Math.sqrt(outer * outer - dy * dy);
            int x0 = (int) Math.ceil(center - 0.5 - xo), x1 = (int) Math.floor(center - 0.5 + xo);
            if (x1 < x0) continue;
            if (dy * dy >= inner * inner) out.add(new Rect(x0, y, x1 - x0 + 1, 1));
            else {
                double xi = Math.sqrt(inner * inner - dy * dy);
                int a = (int) Math.floor(center - 0.5 - xi), z = (int) Math.ceil(center - 0.5 + xi);
                if (a >= x0) out.add(new Rect(x0, y, a - x0 + 1, 1));
                if (x1 >= z) out.add(new Rect(z, y, x1 - z + 1, 1));
            }
        }
    }
    public static List<Rect> region(List<Rect> rects, List<Rect> holes) {
        var edges = new TreeSet<Integer>();
        for (Rect r : rects) if (r.width > 0 && r.height > 0) { edges.add(r.y); edges.add(r.y + r.height); }
        for (Rect r : holes) if (r.width > 0 && r.height > 0) { edges.add(r.y); edges.add(r.y + r.height); }
        var ys = edges.stream().mapToInt(Integer::intValue).toArray(); var out = new ArrayList<Rect>();
        for (int i = 0; i + 1 < ys.length; i++) {
            var solid = spans(rects, ys[i], ys[i + 1]); var minus = spans(holes, ys[i], ys[i + 1]);
            for (int[] span : solid) {
                int start = span[0];
                for (int[] hole : minus) {
                    if (hole[1] <= start || hole[0] >= span[1]) continue;
                    if (hole[0] > start) out.add(new Rect(start, ys[i], hole[0] - start, ys[i + 1] - ys[i]));
                    start = Math.max(start, hole[1]);
                }
                if (start < span[1]) out.add(new Rect(start, ys[i], span[1] - start, ys[i + 1] - ys[i]));
            }
        }
        return List.copyOf(out);
    }
    private static List<int[]> spans(List<Rect> rects, int y0, int y1) {
        var raw = new ArrayList<int[]>();
        for (Rect r : rects) if (r.width > 0 && r.height > 0 && r.y <= y0 && r.y + r.height >= y1) raw.add(new int[]{r.x, r.x + r.width});
        raw.sort(Comparator.comparingInt(a -> a[0])); var out = new ArrayList<int[]>();
        for (int[] r : raw) {
            if (!out.isEmpty() && r[0] <= out.getLast()[1]) out.getLast()[1] = Math.max(out.getLast()[1], r[1]); else out.add(r);
        }
        return out;
    }
}
