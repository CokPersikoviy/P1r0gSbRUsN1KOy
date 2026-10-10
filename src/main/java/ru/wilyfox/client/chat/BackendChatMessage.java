package ru.wilyfox.client.chat;

/** Identity and time are assigned by the authenticated backend. Text stays literal. */
public record BackendChatMessage(long id, String name, String text, long sentAt) {
    public void validate() {
        if (id <= 0 || name == null || !name.matches("[A-Za-z0-9_]{1,16}") || text == null || text.isBlank()
                || !text.equals(text.strip()) || text.codePointCount(0, text.length()) > 256
                || text.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '\u00a7')
                || sentAt < 1_600_000_000_000L || sentAt > System.currentTimeMillis() + 60_000)
            throw new IllegalArgumentException("Invalid backend chat message");
    }
}
