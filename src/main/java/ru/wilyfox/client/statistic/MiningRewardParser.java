package ru.wilyfox.client.statistic;

import ru.wilyfox.client.chat.ChatTimestampFormatter;
import ru.wilyfox.client.hud.config.MiningStatisticsConfig.Source;
import ru.wilyfox.utils.Formatting;

import java.util.Locale;
import java.util.regex.Pattern;

/** Server reward messages only; player chat never matches the required leading phrases. */
public final class MiningRewardParser {
    private static final Pattern AMOUNT = Pattern.compile("(?<![\\p{L}\\d.,])([0-9]+(?:[.,][0-9]+)?)\\s*([KMBTQ])(?=$|[^\\p{L}])", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONEY = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*(?:\\$|монет|денег|\\ue135)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHARDS = Pattern.compile("^(?:Вы нашли шард|Вы нашли шарды)(?:!|:|\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTIPLICITY = Pattern.compile("(?:[xх×]\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE);
    private MiningRewardParser() {}
    public record Reward(Source source, double money, long shards) {}

    public static String normalize(String text) {
        return ChatTimestampFormatter.stripTimestampPrefix(Formatting.stripMinecraftFormatting(text == null ? "" : text))
                .replace('\u00a0', ' ').strip();
    }
    public static double actionBarPrice(String text) {
        String value = normalize(text);
        int plus = value.indexOf('+');
        if (plus < 0) return 0;
        String gain = value.substring(plus + 1).stripLeading();
        var amount = AMOUNT.matcher(gain);
        if (amount.lookingAt()) return parse(amount.group(1), amount.group(2));
        var money = MONEY.matcher(gain);
        return money.lookingAt() ? parse(money.group(1), "") : 0;
    }
    public static Reward reward(String text) {
        String message = normalize(text);
        var shards = SHARDS.matcher(message);
        if (shards.find()) {
            var multiplier = MULTIPLICITY.matcher(message.substring(shards.end()));
            long count = 1;
            if (multiplier.find()) {
                try { count = Long.parseLong(multiplier.group(1)); }
                catch (NumberFormatException ignored) { return null; }
            }
            return count > 0 ? new Reward(null, 0, count) : null;
        }
        Source source = source(message);
        if (source == null) return null;
        double money = 0;
        var amounts = AMOUNT.matcher(message);
        while (amounts.find()) money = parse(amounts.group(1), amounts.group(2));
        if (money == 0) {
            var values = MONEY.matcher(message);
            while (values.find()) money = parse(values.group(1), "");
        }
        return money > 0 ? new Reward(source, money, 0) : null;
    }
    private static Source source(String text) {
        if (text.startsWith("Фиолетовая бочка") || text.startsWith("Красная бочка") || text.startsWith("Зелёная бочка")
                || text.startsWith("Зеленая бочка") || text.startsWith("Обычная бочка")) return Source.BARRELS;
        if (text.startsWith("Руна")) return Source.RUNES;
        if (text.startsWith("Вы взорвали")) return Source.BOMBS;
        if (text.startsWith("Вы сломали всю шахту")) return Source.PETS;
        if (text.startsWith("Вы сломали")) return text.toLowerCase(Locale.ROOT).contains("рун") ? Source.RUNES : Source.WANDS;
        if (text.startsWith("Сокрушающий меч разрубил") || text.startsWith("Залп стрел")
                || text.startsWith("Пожиратель блоков прокатился") || text.startsWith("Чёрная дыра поглотила")
                || text.startsWith("Черная дыра поглотила")) return Source.MULTITOOL;
        return null;
    }
    private static double parse(String value, String suffix) {
        try {
            double base = Double.parseDouble(value.replace(',', '.'));
            int exponent = switch (suffix.toUpperCase(Locale.ROOT)) {
                case "K" -> 3; case "M" -> 6; case "B" -> 9; case "T" -> 12; case "Q" -> 15; default -> 0;
            };
            double result = base * Math.pow(10, exponent);
            return Double.isFinite(result) && result > 0 && result <= 1e30 ? result : 0;
        } catch (NumberFormatException ignored) { return 0; }
    }
}
