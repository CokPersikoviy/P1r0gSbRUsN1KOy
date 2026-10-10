package ru.wilyfox.client.hud.config;

import ru.wilyfox.client.chat.ChatTab;

/** Content settings belong to a window; layouts only override its placement. */
public final class ChatWidgetConfig {
    public ChatTab channel;
    public int width = 280;
    public int rows = 10;
    public boolean showTitle = true;
    public boolean detached;
    /** Built-in identities stay persisted so deleted tabs do not reappear on load. */
    public boolean deleted;
    public String title = "";
    public String textFilter = "";

    public ChatWidgetConfig() {}
    public ChatWidgetConfig(ChatTab channel) { this.channel = channel; }

    public void sanitize(ChatTab fallback) {
        if (channel == null) channel = fallback;
        width = Math.clamp(width, 100, 600);
        rows = Math.clamp(rows, 2, 30);
        if (title == null || title.isBlank()) title = channel.getTitle();
        title = title.strip().substring(0, Math.min(20, title.strip().length()));
        if (textFilter == null) textFilter = "";
        textFilter = textFilter.substring(0, Math.min(128, textFilter.length()));
    }
}
