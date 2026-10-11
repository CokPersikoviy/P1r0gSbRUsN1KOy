package ru.wilyfox.client.chat;

/** Identity and time are assigned by the authenticated backend. Text stays literal. */
public record BackendChatMessage(long id, String name, String text, long sentAt, String source, java.util.List<Attachment> attachments) {
    public BackendChatMessage(long id, String name, String text, long sentAt) { this(id, name, text, sentAt, null, null); }
    public boolean fromDiscord() { return "discord".equals(source); }
    public java.util.List<Attachment> images() { return attachments == null ? java.util.List.of() : attachments; }
    public record Attachment(String id, String name, int width, int height) {
        public void validate() {
            if (id == null || !id.matches("0|[0-9]{17,20}") || !safeLiteral(name, 80) || name.isBlank()
                    || width <= 0 || height <= 0 || width > 8192 || height > 8192 || (long) width * height > 16_777_216)
                throw new IllegalArgumentException("Invalid chat attachment");
        }
    }
    private static boolean safeLiteral(String value, int limit) {
        return value != null && value.equals(value.strip()) && value.codePointCount(0, value.length()) <= limit
                && value.codePoints().noneMatch(c -> Character.isISOControl(c) || c == '\u00a7');
    }
    public void validate() {
        if (id <= 0 || source != null && !source.equals("minecraft") && !fromDiscord()
                || name == null || (fromDiscord() ? !safeLiteral(name, 80) || name.isBlank() : !name.matches("[A-Za-z0-9_]{1,16}"))
                || !safeLiteral(text, fromDiscord() ? 2000 : 256) || text.isBlank() && images().isEmpty() || images().size() > 4
                || sentAt < 1_600_000_000_000L || sentAt > 4_102_444_800_000L)
            throw new IllegalArgumentException("Invalid backend chat message");
        var ids = new java.util.HashSet<String>();
        for (var image : images()) { if (image == null) throw new IllegalArgumentException("Missing chat attachment"); image.validate();
            if (!ids.add(image.id())) throw new IllegalArgumentException("Duplicate chat attachment"); }
    }
    /** Check wire timestamps against the backend clock; the local archive preserves server time. */
    public void validateAt(long backendNow) {
        validate();
        if (sentAt > backendNow + 60_000) throw new IllegalArgumentException("Chat timestamp ahead of backend clock");
    }
}
