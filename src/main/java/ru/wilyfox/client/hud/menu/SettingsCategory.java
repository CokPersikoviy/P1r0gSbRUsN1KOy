package ru.wilyfox.client.hud.menu;

public enum SettingsCategory {
    QUICK_ACCESS("Quick Access"),
    AUTO_MESSAGES("Auto Messages"),
    BOSS_TIMERS("Boss Timers"),
    BOSS_RESPAWN_MESSAGES("Boss Messages"),
    PLAYER_HEALTH_BARS("HP Bars"),
    FISHING("Fishing"),
    POP_UPS("Pop-Ups"),
    DISCORD("Discord"),
    THEME("Theme"),
    ALCHEMY("Alchemy"),
    RENDER("Render"),
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
