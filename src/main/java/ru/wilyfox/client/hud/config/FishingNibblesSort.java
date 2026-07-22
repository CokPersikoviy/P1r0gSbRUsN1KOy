package ru.wilyfox.client.hud.config;

public enum FishingNibblesSort {
    NIBBLE("By nibble"),
    DIMENSION("By dimension");

    private final String displayName;

    FishingNibblesSort(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
