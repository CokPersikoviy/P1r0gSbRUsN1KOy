package ru.wilyfox.client.hud.config;

public enum LowHpMessageElement {
    SERVER("Subserver", "&e"),
    NAME("Boss name", "&6"),
    LEVEL("Boss level", "&b"),
    CURSE("Curse", "&3"),
    HEALTH("Health", "&c"),
    PERCENT("HP percent", "&e"),
    STAGE("Stage / label", "&7");

    private final String title;
    private final String defaultColor;

    LowHpMessageElement(String title, String defaultColor) {
        this.title = title;
        this.defaultColor = defaultColor;
    }

    public String title() { return title; }
    public String defaultColor() { return defaultColor; }
}
