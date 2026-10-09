package ru.wilyfox.client.hud.config;

import ru.wilyfox.client.protocol.DwGameLocation;
import java.util.*;

/** Exact server IDs, optional prefix wildcards, and explicit groups of locations. */
public final class LocationSelectors {
    private static final Set<String> LEGACY_FISHING = Set.of("fish_1_overworld", "fish_2_overworld", "fish_nether", "fish_end");
    private LocationSelectors() {}
    public static String normalize(String id) { return id == null ? "" : id.strip().toLowerCase(Locale.ROOT); }
    public static Set<String> sanitize(Set<String> source) {
        var result = new LinkedHashSet<String>();
        if (source != null) for (String item : source) {
            String id = normalize(item);
            if (!id.isEmpty() && id.length() <= 128) result.add(id);
        }
        return result;
    }
    public static boolean matches(Set<String> selectors, String currentId) {
        if (selectors == null || selectors.isEmpty()) return false;
        String id = normalize(currentId);
        if (id.isEmpty()) return false;
        var location = new DwGameLocation(id);
        for (String selector : selectors) {
            if (selector == null) continue;
            boolean matches = switch (selector) {
                case "#fishing" -> location.isFishing() || LEGACY_FISHING.contains(id);
                case "#boss" -> location.isBoss();
                case "#shaft" -> location.isMine();
                case "#dungeon" -> location.isAnyDungeon();
                case "#spawn" -> location.isSpawn();
                case "#siege" -> location.isSiege();
                default -> selector.endsWith("*") ? id.startsWith(selector.substring(0, selector.length() - 1)) : id.equals(selector);
            };
            if (matches) return true;
        }
        return false;
    }
}
