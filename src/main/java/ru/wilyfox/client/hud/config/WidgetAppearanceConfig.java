package ru.wilyfox.client.hud.config;

/** Multipliers over the existing theme; 100% preserves the widget's current appearance. */
public final class WidgetAppearanceConfig {
    public int opacityPercent = 100;
    public int backgroundOpacityPercent = 100;
    public void sanitize() {
        opacityPercent = Math.clamp(opacityPercent, 0, 100);
        backgroundOpacityPercent = Math.clamp(backgroundOpacityPercent, 0, 100);
    }
    public float opacity() { return Math.clamp(opacityPercent, 0, 100) / 100f; }
    public float backgroundOpacity() { return Math.clamp(backgroundOpacityPercent, 0, 100) / 100f; }
}
