package ru.wilyfox.client.protocol;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CurrentServerInfo(String family, int serverNumber, int mirror, String rawName) {
    private static final Pattern SERVER_NAME_PATTERN = Pattern.compile("^([A-Z]+)(\\d*)$");
    private static final CurrentServerInfo UNKNOWN = new CurrentServerInfo("UNKNOWN", 0, 0, "");
    /** TAB text without "#N"; the protocol always reports a real mirror, and #0 is a real mirror. */
    public static final int NO_MIRROR = -1;

    public static CurrentServerInfo unknown() {
        return UNKNOWN;
    }

    public static CurrentServerInfo fromProtocol(String serverName, int mirror) {
        if (serverName == null || serverName.isBlank()) {
            return unknown();
        }

        Matcher matcher = SERVER_NAME_PATTERN.matcher(serverName.trim().toUpperCase(Locale.ROOT));
        if (!matcher.matches()) {
            return new CurrentServerInfo(serverName.trim().toUpperCase(Locale.ROOT), 0, Math.max(0, mirror), serverName.trim());
        }

        String family = matcher.group(1);
        int serverNumber = parseIntOrZero(matcher.group(2));
        return new CurrentServerInfo(family, serverNumber, Math.max(0, mirror), serverName.trim());
    }

    public static CurrentServerInfo fromDisplayText(String text) {
        if (text == null || text.isBlank()) {
            return unknown();
        }

        String normalized = text.trim();
        Matcher prisonMatcher = Pattern.compile("(?i)prisonevo[-\\s]?(\\d+)(?:\\s*#\\s*(\\d+))?").matcher(normalized);
        if (prisonMatcher.find()) {
            return new CurrentServerInfo(
                    "PRISONEVO",
                    parseIntOrZero(prisonMatcher.group(1)),
                    prisonMatcher.group(2) == null ? NO_MIRROR : parseIntOrZero(prisonMatcher.group(2)),
                    normalized
            );
        }

        if (normalized.toLowerCase(Locale.ROOT).contains("hub") || normalized.contains("Хаб")) {
            return new CurrentServerInfo("HUB", 0, 0, normalized);
        }

        return unknown();
    }

    public boolean isKnown() {
        return !"UNKNOWN".equals(family) && !family.isBlank();
    }

    public boolean hasMirror() {
        return mirror != NO_MIRROR;
    }

    public String displayName() {
        return switch (family) {
            case "HUB" -> "Хаб";
            case "PRISONEVO" -> {
                String base = serverNumber > 0 ? "PrisonEvo-" + serverNumber : "PrisonEvo";
                yield hasMirror() ? base + " #" + mirror : base;
            }
            case "UNKNOWN" -> "";
            default -> rawName == null ? family : rawName;
        };
    }

    private static int parseIntOrZero(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
