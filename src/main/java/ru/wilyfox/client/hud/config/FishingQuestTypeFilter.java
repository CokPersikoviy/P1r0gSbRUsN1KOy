package ru.wilyfox.client.hud.config;

public enum FishingQuestTypeFilter {
    ALL("All"),
    NORMAL("Overworld"),
    NETHER("Nether"),
    END("End");

    private final String displayName;

    FishingQuestTypeFilter(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
