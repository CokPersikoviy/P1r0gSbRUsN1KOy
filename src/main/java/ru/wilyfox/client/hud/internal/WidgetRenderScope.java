package ru.wilyfox.client.hud.internal;

import java.util.List;

/** Scoped to our widgets only; deferred text/item preparation enters the same opacity scope. */
public final class WidgetRenderScope implements AutoCloseable {
    private static final ThreadLocal<WidgetRenderScope> CURRENT = new ThreadLocal<>();
    private final WidgetRenderScope previous;
    private final List<Object> capture;
    private final float alpha;
    private final float backgroundAlpha;
    private final float snapshotAlpha;
    private final java.util.Map<Object, Float> captureOpacities;
    private final org.joml.Matrix3x2fc transform;
    public WidgetRenderScope(List<Object> capture, float alpha) {
        this(capture, alpha, null);
    }
    public WidgetRenderScope(List<Object> capture, float alpha, org.joml.Matrix3x2fc transform) {
        this(capture, alpha, transform, null, 1, 1);
    }
    public WidgetRenderScope(List<Object> capture, float alpha, org.joml.Matrix3x2fc transform, java.util.Map<Object, Float> captureOpacities) {
        this(capture, alpha, transform, captureOpacities, 1, 1);
    }
    private WidgetRenderScope(List<Object> capture, float alpha, org.joml.Matrix3x2fc transform,
                              java.util.Map<Object, Float> captureOpacities, float backgroundAlpha, float snapshotAlpha) {
        previous = CURRENT.get(); this.capture = capture; this.alpha = alpha; this.transform = transform;
        this.captureOpacities = captureOpacities; this.backgroundAlpha = backgroundAlpha; this.snapshotAlpha = snapshotAlpha;
        CURRENT.set(this);
    }
    public static org.joml.Matrix3x2fc transform() { var scope = CURRENT.get(); return scope == null ? null : scope.transform; }
    public static float alpha() { var scope = CURRENT.get(); return scope == null ? 1 : scope.alpha; }
    public static float backgroundAlpha() { var scope = CURRENT.get(); return scope == null ? 1 : scope.backgroundAlpha; }
    public static WidgetRenderScope appearance(ru.wilyfox.client.hud.widget.Widget widget) {
        String key = widget instanceof ru.wilyfox.client.hud.widget.AbstractWidget placed ? placed.getConfigKey() : widget.getClass().getSimpleName();
        var config = ru.wilyfox.client.hud.config.ConfigManager.get().widgetAppearance.get(key);
        if (config == null || config.opacity() == 1 && config.backgroundOpacity() == 1) return null;
        var scope = CURRENT.get();
        return new WidgetRenderScope(scope == null ? null : scope.capture, alpha() * config.opacity(), transform(),
                scope == null ? null : scope.captureOpacities, backgroundAlpha() * config.backgroundOpacity(),
                (scope == null ? 1 : scope.snapshotAlpha) * config.opacity());
    }
    /** Background-only fade also covers the blurred backdrop and decorative panel edges. */
    public static WidgetRenderScope background() { return backgroundAlpha() == 1 ? null : fade(backgroundAlpha()); }
    /** Editor-only styling for references inherited from Main; it never changes saved opacity. */
    public static WidgetRenderScope editorReference() {
        var scope = CURRENT.get();
        return new WidgetRenderScope(scope == null ? null : scope.capture, alpha() * .65f, transform(),
                scope == null ? null : scope.captureOpacities, backgroundAlpha() * .35f,
                (scope == null ? 1 : scope.snapshotAlpha) * .65f);
    }
    /** Preserve a layout animation's capture and transform when a widget also fades its content. */
    public static WidgetRenderScope fade(float multiplier) {
        var scope = CURRENT.get();
        return new WidgetRenderScope(scope == null ? null : scope.capture, alpha() * multiplier,
                scope == null ? null : scope.transform, scope == null ? null : scope.captureOpacities,
                backgroundAlpha(), (scope == null ? 1 : scope.snapshotAlpha) * multiplier);
    }
    public static void capture(Object state) {
        var scope = CURRENT.get();
        if (scope != null && scope.capture != null) {
            scope.capture.add(state);
            if (scope.captureOpacities != null && scope.snapshotAlpha < 1) scope.captureOpacities.put(state, scope.snapshotAlpha);
        }
    }
    @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
}
