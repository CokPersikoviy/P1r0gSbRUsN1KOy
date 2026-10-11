package ru.wilyfox.client.resource;

import ru.wilyfox.utils.Formatting;
import java.util.Locale;
import java.util.regex.Pattern;

/** Duration and precision of account reward countdowns, from holograms or item lore. */
public record ResourceCountdown(long millis, long precisionMillis) {
    private static final Pattern VALUE = Pattern.compile("(\\d+)\\s*(д(?:н(?:я|ей|ь)?)?\\.?|ч(?:ас(?:а|ов)?)?\\.?|мин(?:ут(?:а|ы)?)?\\.?|сек(?:унд(?:а|ы)?)?\\.?|с\\.?)", Pattern.UNICODE_CASE);
    public static ResourceCountdown parse(String text) {
        return parseAfter(text, "до следующего прокрута:");
    }
    public static ResourceCountdown parseCredits(String text) {
        return parseAfter(text, "новые кредиты через:");
    }
    private static ResourceCountdown parseAfter(String text, String prefix) {
        String clean = Formatting.stripMinecraftFormatting(text == null ? "" : text).toLowerCase(Locale.ROOT).replace('\u00a0', ' ').trim();
        if (!clean.startsWith(prefix)) return null;
        var matcher = VALUE.matcher(clean.substring(clean.indexOf(':') + 1));
        long total = 0, precision = Long.MAX_VALUE;
        boolean found = false;
        int end = clean.indexOf(':') + 1;
        try {
            while (matcher.find()) {
                int start = clean.indexOf(':') + 1 + matcher.start();
                if (!clean.substring(end, start).isBlank()) return null;
                end = clean.indexOf(':') + 1 + matcher.end();
                String unit = matcher.group(2);
                long multiplier = unit.startsWith("д") ? 86_400_000L : unit.startsWith("ч") ? 3_600_000L : unit.startsWith("мин") ? 60_000L : 1000L;
                total = Math.addExact(total, Math.multiplyExact(Long.parseLong(matcher.group(1)), multiplier));
                precision = Math.min(precision, multiplier); found = true;
            }
        } catch (ArithmeticException | NumberFormatException ignored) { return null; }
        return found && clean.substring(end).isBlank() && total <= 31L * 86_400_000L ? new ResourceCountdown(total, precision) : null;
    }
}
