package ru.wilyfox.client.moduser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable, memory-only roster. Only validated backend snapshots may replace it. */
public final class PresenceStore {
    private static Map<String, String> online = Map.of();
    private static List<String> names = List.of();
    private static Map<String, SocialWire.Player> details = Map.of();

    private PresenceStore() {}

    static void replace(List<String> players) {
        if (players == null || players.size() > 256) throw new IllegalArgumentException("Invalid roster");
        Map<String, String> next = new LinkedHashMap<>();
        for (String name : players) {
            if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) {
                throw new IllegalArgumentException("Invalid player name");
            }
            next.put(name.toLowerCase(Locale.ROOT), name);
        }
        online = Map.copyOf(next);
        names = next.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        details = Map.of();
    }

    static void replacePlayers(List<SocialWire.Player> players) {
        replace(players.stream().map(SocialWire.Player::name).toList());
        var next = new LinkedHashMap<String, SocialWire.Player>();
        for (var player : players) next.put(player.name().toLowerCase(Locale.ROOT), player);
        details = Map.copyOf(next);
    }
    static SocialWire.Player player(String name) { return name == null ? null : details.get(name.toLowerCase(Locale.ROOT)); }

    static void clear() {
        online = Map.of();
        names = List.of();
        details = Map.of();
    }

    public static boolean isKnown(String name) {
        return name != null && online.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public static int knownCount() { return names.size(); }
    public static List<String> knownDisplayNames() { return names; }
}
