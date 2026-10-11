package ru.wilyfox.client.hud.config;

public enum ChatWidgetVisibility {
    ALWAYS("Always"),
    CHAT_OPEN("Chat open");

    private final String title;
    ChatWidgetVisibility(String title) { this.title = title; }
    public String title() { return title; }

    /** Ten seconds after a matching live message, with a two-second fade at the end. */
    public float alpha(boolean chatOpen, long messageAgeNanos) {
        if (this == ALWAYS || chatOpen) return 1;
        if (messageAgeNanos < 0) return 0;
        return Math.clamp((10_000_000_000L - messageAgeNanos) / 2_000_000_000f, 0f, 1f);
    }
}
