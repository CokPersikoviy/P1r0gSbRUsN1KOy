package ru.wilyfox.client.hud.internal;

import java.util.List;

/** Scoped to our widgets only; deferred text/item preparation enters the same opacity scope. */
public final class WidgetRenderScope implements AutoCloseable {
    private static final ThreadLocal<WidgetRenderScope> CURRENT = new ThreadLocal<>();
    private final WidgetRenderScope previous;
    private final List<Object> capture;
    private final float alpha;
    private final org.joml.Matrix3x2fc transform;
    public WidgetRenderScope(List<Object> capture, float alpha) {
        this(capture, alpha, null);
    }
    public WidgetRenderScope(List<Object> capture, float alpha, org.joml.Matrix3x2fc transform) {
        previous = CURRENT.get(); this.capture = capture; this.alpha = alpha; this.transform = transform; CURRENT.set(this);
    }
    public static org.joml.Matrix3x2fc transform() { var scope = CURRENT.get(); return scope == null ? null : scope.transform; }
    public static float alpha() { var scope = CURRENT.get(); return scope == null ? 1 : scope.alpha; }
    public static void capture(Object state) {
        var scope = CURRENT.get();
        if (scope != null && scope.capture != null) scope.capture.add(state);
    }
    @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
}
