package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.hud.config.ChatTimestampConfig;
import ru.wilyfox.client.hud.config.ConfigManager;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import java.util.EnumMap;
import java.util.Locale;

public final class ChatTimestampFormatter {
    private static final EnumMap<ChatTimestampConfig.Format, DateTimeFormatter> FORMATS = new EnumMap<>(ChatTimestampConfig.Format.class);
    private static final String TIME = "\\d{2}:\\d{2}(?::\\d{2})?(?: [AP]M)?";
    private static final Pattern TIMESTAMP_PREFIX = Pattern.compile(
            "^(?:\\[" + TIME + "]|\\(" + TIME + "\\)|<" + TIME + ">|" + TIME + "(?=\\s))\\s*");
    static {
        for (var format : ChatTimestampConfig.Format.values())
            FORMATS.put(format, DateTimeFormatter.ofPattern(format.pattern, Locale.ROOT));
    }

    private ChatTimestampFormatter() {
    }

    public static MutableComponent createTimestampPrefix() {
        return createTimestampPrefix(Instant.now());
    }

    public static MutableComponent createTimestampPrefix(Instant timestamp) {
        return createTimestampPrefix(timestamp, ConfigManager.get().render.timestamp);
    }

    public static MutableComponent createTimestampPrefix(Instant timestamp, ChatTimestampConfig settings) {
        LocalTime time = timestamp == null
                ? LocalTime.now()
                : LocalTime.ofInstant(timestamp, ZoneId.systemDefault());
        var format = settings.format == null ? ChatTimestampConfig.Format.TIME_SECONDS : settings.format;
        var brackets = settings.brackets == null ? ChatTimestampConfig.Brackets.SQUARE : settings.brackets;
        String value = brackets.opening + time.format(FORMATS.get(format)) + brackets.closing + " ";
        return Component.literal(value).withColor(settings.themeColor ? WidgetTheme.TEXT_MUTED : settings.color & 0xFFFFFF)
                .withStyle(style -> style.withBold(settings.bold).withItalic(settings.italic));
    }

    public static String stripTimestampPrefix(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return TIMESTAMP_PREFIX.matcher(text.stripLeading()).replaceFirst("");
    }
}
