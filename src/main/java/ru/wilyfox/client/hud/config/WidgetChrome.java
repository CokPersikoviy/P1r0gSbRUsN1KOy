package ru.wilyfox.client.hud.config;

/**
 * Widget background intensity ("Frost" design language). Orthogonal to the renderer choice
 * (custom glass vs native GuiGraphics) — this only decides how much surface a widget carries.
 */
public enum WidgetChrome {
    BARE("Bare"),
    SOLID("Solid"),
    FROST("Frost");

    private final String label;

    WidgetChrome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
