package ru.wilyfox.client.hud.config;

public enum FishingWidgetVisibility {
    ALWAYS("Always"),
    FISHING_WARP("Fishing warp"),
    FISHING_ROD("Fishing rod");

    private final String displayName;

    FishingWidgetVisibility(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
