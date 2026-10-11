package ru.wilyfox.client.hud.config;

public enum WidgetListSort {
    ALPHABETICAL("A-Z", "alphabetical"),
    ACTIVE_FIRST("ON", "active_first"),
    INACTIVE_FIRST("OFF", "inactive_first");

    public final String label;
    public final String translationKey;

    WidgetListSort(String label, String key) {
        this.label = label;
        this.translationKey = "froghelper.widgets.sort." + key;
    }
}
