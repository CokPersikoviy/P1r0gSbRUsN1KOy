package ru.wilyfox.client.visuals;

import ru.wilyfox.client.hud.config.VisualsConfig.LineStyle;

/** Bounded subdivision in world units; dash endpoints remain on the original edge. */
public final class VisualLinePieces {
    private VisualLinePieces() {}
    public static void forEach(LineStyle style, double length, Piece consumer) {
        if (!Double.isFinite(length) || length <= 1e-9) return;
        switch (style) {
            case CORNERS -> {
                double arm = Math.min(0.22, length * 0.3) / length;
                consumer.accept(0, arm); consumer.accept(1 - arm, 1);
            }
            case DASHED -> {
                int count = Math.clamp((int) Math.round((length + 1.0 / 12) / (5.0 / 24)), 1, 128);
                double dash = 1.0 / (count + (count - 1) * 2.0 / 3), step = dash * 5.0 / 3;
                for (int i = 0; i < count; i++) consumer.accept(i * step, i == count - 1 ? 1 : i * step + dash);
            }
            default -> consumer.accept(0, 1);
        }
    }
    @FunctionalInterface public interface Piece { void accept(double from, double to); }
}
