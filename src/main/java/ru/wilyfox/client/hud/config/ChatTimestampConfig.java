package ru.wilyfox.client.hud.config;

public final class ChatTimestampConfig {
    public Format format = Format.TIME_SECONDS;
    public Brackets brackets = Brackets.SQUARE;
    public boolean themeColor = true;
    public int color = 0xAAAAAA;
    public boolean bold, italic;

    void sanitize() {
        if (format == null) format = Format.TIME_SECONDS;
        if (brackets == null) brackets = Brackets.SQUARE;
        color &= 0xFFFFFF;
    }

    public enum Format {
        TIME_SECONDS("HH:mm:ss", "24h · seconds"), TIME("HH:mm", "24h · minutes"),
        TIME_12_SECONDS("hh:mm:ss a", "12h · seconds"), TIME_12("hh:mm a", "12h · minutes");
        public final String pattern;
        private final String label;
        Format(String pattern, String label) { this.pattern = pattern; this.label = label; }
        public String label() { return label; }
    }

    public enum Brackets {
        SQUARE("[", "]"), ROUND("(", ")"), ANGLE("<", ">"), NONE("", "");
        public final String opening, closing;
        Brackets(String opening, String closing) { this.opening = opening; this.closing = closing; }
        public String label() { return this == NONE ? "None" : opening + "Time" + closing; }
    }
}
