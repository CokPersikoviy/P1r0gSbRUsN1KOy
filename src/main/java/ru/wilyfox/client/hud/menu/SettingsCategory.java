package ru.wilyfox.client.hud.menu;

public enum SettingsCategory {
    QUICK_ACCESS("Quick Access"),
    AUTO_MESSAGES("Auto Messages"),
    BOSS_RESPAWN_MESSAGES("Boss Messages"),
    LOW_HP_MESSAGES("Low HP Message"),
    PLAYER_HEALTH_BARS("HP Bars"),
    FISHING("Fishing"),
    POP_UPS("Pop-Ups"),
    DISCORD("Discord"),
    THEME("Theme"),
    ALCHEMY("Alchemy"),
    RENDER("Render"),
    VISUALS("Visuals"),
    WIDGET("Widget"),
    RUNES_BAG_KEYBINDS("Runes Bag Keybinds"),
    CLICKER("Clicker");

    private final String title;

    SettingsCategory(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
