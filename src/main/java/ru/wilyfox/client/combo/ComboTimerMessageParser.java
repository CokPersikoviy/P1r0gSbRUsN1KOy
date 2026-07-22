package ru.wilyfox.client.combo;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ComboTimerMessageParser {
    private static final Pattern TIMER_PATTERN = Pattern.compile(
            "Комбо закончится через (\\d+) секунд\\. Продолжите копать, чтобы не потерять его\\."
    );

    private ComboTimerMessageParser() {
    }

    public static OptionalLong parseRemainingSeconds(String text) {
        if (text == null || text.isBlank()) {
            return OptionalLong.empty();
        }

        Matcher matcher = TIMER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return OptionalLong.empty();
        }

        try {
            return OptionalLong.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }
}
