package ru.wilyfox.client.hud.config;

public enum BossTimerElement {
    TITLE("Show title"), ICON("Show icons"), CAPTURE("Show capture"), MYTHICAL("Show mythical event"),
    NAME("Show name"), COLLECTIBLES("Show collectibles"), LEVEL("Show level"), TIMER("Show timer");
    private final String title;
    BossTimerElement(String title) { this.title = title; }
    public String title() { return title; }
    public boolean visible(BossWidgetConfig c) {
        return switch (this) {
            case TITLE -> c.showTitle; case ICON -> c.showIcons; case CAPTURE -> c.showCapture;
            case MYTHICAL -> c.showMythical; case NAME -> c.showName; case COLLECTIBLES -> c.showCollectibles;
            case LEVEL -> c.showLevel; case TIMER -> c.showTimer;
        };
    }
    public void visible(BossWidgetConfig c, boolean value) {
        switch (this) {
            case TITLE -> c.showTitle = value; case ICON -> c.showIcons = value; case CAPTURE -> c.showCapture = value;
            case MYTHICAL -> c.showMythical = value; case NAME -> c.showName = value; case COLLECTIBLES -> c.showCollectibles = value;
            case LEVEL -> c.showLevel = value; case TIMER -> c.showTimer = value;
        }
    }
}
