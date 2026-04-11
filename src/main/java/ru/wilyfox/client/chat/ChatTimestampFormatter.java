package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public final class ChatTimestampFormatter {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern TIMESTAMP_PREFIX = Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}]\\s*");
    private static final int TIMESTAMP_COLOR = 0xFF8C8C8C;

    private ChatTimestampFormatter() {
    }

    public static MutableComponent createTimestampPrefix() {
        return createTimestampPrefix(Instant.now());
    }

    public static MutableComponent createTimestampPrefix(Instant timestamp) {
        LocalTime time = timestamp == null
                ? LocalTime.now()
                : LocalTime.ofInstant(timestamp, ZoneId.systemDefault());
        String value = "[" + time.format(TIME_FORMAT) + "] ";
        return Component.literal(value).withColor(TIMESTAMP_COLOR);
    }

    public static String stripTimestampPrefix(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return TIMESTAMP_PREFIX.matcher(text.stripLeading()).replaceFirst("");
    }
}
