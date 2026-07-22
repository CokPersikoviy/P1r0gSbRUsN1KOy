package ru.wilyfox.client.hud.config;

import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

public enum PotionRecipeVisibility {
    ALWAYS("Always"),
    ALCHEMY("In alchemy");

    private final String title;

    PotionRecipeVisibility(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public boolean isVisible() {
        if (this == ALWAYS) {
            return true;
        }

        return DiamondWorldProtocolClient.isCurrentAlchemyLocation();
    }
}
