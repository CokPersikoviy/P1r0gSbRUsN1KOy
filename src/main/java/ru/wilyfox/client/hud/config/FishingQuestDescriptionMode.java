package ru.wilyfox.client.hud.config;

public enum FishingQuestDescriptionMode {
    ALWAYS("Always"),
    FISHING_WARP("Fishing warp"),
    FISHING_ROD("Fishing rod");

    private final String displayName;

    FishingQuestDescriptionMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
