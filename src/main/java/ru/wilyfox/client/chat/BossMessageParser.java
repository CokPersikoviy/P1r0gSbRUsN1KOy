package ru.wilyfox.client.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.wilyfox.utils.Formatting.stripMinecraftFormatting;

final class BossMessageParser {
    private static final Pattern CURSE = Pattern.compile("Босс проклят! Особенность: ([А-Яа-яЁё ]+)");
    private static final Pattern CAPTURE = Pattern.compile("^Босс (.*) захвачен кланом (.*)!$");
    private static final Pattern CLAN_WAVE = Pattern.compile("Испытание вызова (?:(|\\d+):|)(\\d+)");
    private static final Pattern HEALTH = Pattern.compile("^(.+?)\\s+(\\d+(?:[.,]\\d+)?)\\D*$");

    private BossMessageParser() {
    }

    static String parseCurse(String text) {
        Matcher matcher = CURSE.matcher(clean(text));
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    static BossCapture parseCapture(String text) {
        Matcher matcher = CAPTURE.matcher(clean(text));
        if (!matcher.matches()) {
            return null;
        }
        return new BossCapture(matcher.group(1).trim(), matcher.group(2).trim());
    }

    static BossBarText parseBossBar(String text) {
        String clean = clean(text);
        if (clean.isEmpty() || CLAN_WAVE.matcher(clean).find()) {
            return null;
        }

        Matcher matcher = HEALTH.matcher(clean);
        if (!matcher.matches()) {
            return null;
        }

        String bossName = matcher.group(1).trim();
        if (bossName.startsWith("Босс ")) {
            bossName = bossName.substring(5).trim();
        }
        if (bossName.isEmpty()) {
            return null;
        }

        try {
            return new BossBarText(bossName, Double.parseDouble(matcher.group(2).replace(',', '.')));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String clean(String text) {
        return stripMinecraftFormatting(text).replace('\u00A0', ' ').trim();
    }

    record BossCapture(String bossName, String clanName) {
    }

    record BossBarText(String bossName, double health) {
    }
}
