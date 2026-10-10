package ru.wilyfox.client.chat;

public enum ChatTab {
    ALL("ALL", "", new String[0]),
    FH("FH", "", new String[0]),
    GLOBAL("G", "Ⓖ", new String[]{"G", "Ⓖ"}),
    TRADE("T", "Ⓜ", new String[]{"T", "Ⓜ"}),
    LOCAL("L", "Ⓛ", new String[]{"L", "Ⓛ"}),
    CLAN("C", "C", new String[]{"C", "[Клан]"}),
    PRIVATE("PM", "ЛС", new String[]{"PM", "ЛС"});

    private final String title;
    private final String chatPrefix;
    private final String[] resolvePrefixes;

    ChatTab(String title, String chatPrefix, String[] resolvePrefixes) {
        this.title = title;
        this.chatPrefix = chatPrefix;
        this.resolvePrefixes = resolvePrefixes;
    }

    public String getTitle() {
        return title;
    }

    public String getChatPrefix() {
        return chatPrefix;
    }

    public String[] getResolvePrefixes() {
        return resolvePrefixes;
    }
}
