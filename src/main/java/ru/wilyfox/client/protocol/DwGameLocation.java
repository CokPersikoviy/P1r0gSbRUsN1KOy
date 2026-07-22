package ru.wilyfox.client.protocol;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record DwGameLocation(String id) {
    private static final Set<String> FISHING_SPOT_IDS = Set.of(
            "bay",
            "azurepond",
            "citycanal",
            "swamp",
            "ambergrot",
            "basalt",
            "netherval",
            "magma",
            "endwharf",
            "silence",
            "crystal"
    );

    private static final Map<String, String> LOCATION_NAMES = Map.ofEntries(
            Map.entry("spawn_overworld", "\u0421\u043f\u0430\u0432\u043d"),
            Map.entry("arena_overworld", "\u0410\u0440\u0435\u043d\u0430"),
            Map.entry("library_overworld", "\u0411\u0438\u0431\u043b\u0438\u043e\u0442\u0435\u043a\u0430"),
            Map.entry("wand_overworld", "\u0421\u043f\u0430\u0432\u043d"),
            Map.entry("spawn_nether", "\u0410\u0434\u0441\u043a\u0438\u0439 \u0441\u043f\u0430\u0432\u043d"),
            Map.entry("arena_nether", "\u0410\u0434\u0441\u043a\u0430\u044f \u0430\u0440\u0435\u043d\u0430"),
            Map.entry("library_nether", "\u0410\u0434\u0441\u043a\u0430\u044f \u0431\u0438\u0431\u043b\u0438\u043e\u0442\u0435\u043a\u0430"),
            Map.entry("wand_nether", "\u0410\u0434\u0441\u043a\u0438\u0439 \u0441\u043f\u0430\u0432\u043d"),
            Map.entry("spawn_end", "\u042d\u043d\u0434\u0435\u0440 \u0441\u043f\u0430\u0432\u043d"),
            Map.entry("arena_end", "\u042d\u043d\u0434\u0435\u0440 \u0430\u0440\u0435\u043d\u0430"),
            Map.entry("library_end", "\u042d\u043d\u0434\u0435\u0440 \u0431\u0438\u0431\u043b\u0438\u043e\u0442\u0435\u043a\u0430"),
            Map.entry("wand_end", "\u042d\u043d\u0434\u0435\u0440 \u0441\u043f\u0430\u0432\u043d"),
            Map.entry("wand", "\u0421\u043f\u0430\u0432\u043d"),
            Map.entry("miner", "\u0421\u043f\u0430\u0432\u043d"),
            Map.entry("craft", "\u0421\u043f\u0430\u0432\u043d"),
            Map.entry("alchemy", "\u0410\u043b\u0445\u0438\u043c\u0438\u044f"),
            Map.entry("pvp", "PvP \u0430\u0440\u0435\u043d\u0430"),
            Map.entry("market", "\u0420\u044b\u043d\u043e\u043a"),
            Map.entry("auction", "\u0410\u0443\u043a\u0446\u0438\u043e\u043d"),
            Map.entry("duels", "\u0414\u0443\u044d\u043b\u0438"),
            Map.entry("fish_1_overworld", "\u0420\u044b\u0431\u0430\u043b\u043a\u0430"),
            Map.entry("fish_2_overworld", "\u0420\u044b\u0431\u0430\u043b\u043a\u0430"),
            Map.entry("fish_nether", "\u0410\u0434\u0441\u043a\u0430\u044f \u0440\u044b\u0431\u0430\u043b\u043a\u0430"),
            Map.entry("fish_end", "\u042d\u043d\u0434\u0435\u0440 \u0440\u044b\u0431\u0430\u043b\u043a\u0430"),
            Map.entry("mine", "\u0428\u0430\u0445\u0442\u0435\u0440\u0441\u043a\u0430\u044f"),
            Map.entry("clanarena", "\u041a\u043b\u0430\u043d\u043e\u0432\u0430\u044f \u0430\u0440\u0435\u043d\u0430"),
            Map.entry("clan_base", "\u041a\u043b\u0430\u043d\u043e\u0432\u0430\u044f \u0431\u0430\u0437\u0430"),
            Map.entry("tower", "\u0411\u0430\u0448\u043d\u044f"),
            Map.entry("temple_arena", "\u041f\u0440\u043e\u043a\u043b\u044f\u0442\u044b\u0439 \u0445\u0440\u0430\u043c")
    );

    private static final Map<String, String> FISHING_SPOT_NAMES = Map.ofEntries(
            Map.entry("bay", "\u0420\u044b\u0431\u0430\u0446\u043a\u0430\u044f \u0431\u0443\u0445\u0442\u0430"),
            Map.entry("azurepond", "\u041b\u0430\u0437\u0443\u0440\u043d\u044b\u0439 \u043f\u0440\u0443\u0434"),
            Map.entry("citycanal", "\u0413\u043e\u0440\u043e\u0434\u0441\u043a\u0438\u0435 \u043a\u0430\u043d\u0430\u043b\u044b"),
            Map.entry("swamp", "\u0411\u043e\u043b\u043e\u0442\u043e"),
            Map.entry("ambergrot", "\u042f\u043d\u0442\u0430\u0440\u043d\u044b\u0439 \u0433\u0440\u043e\u0442"),
            Map.entry("basalt", "\u0411\u0430\u0437\u0430\u043b\u044c\u0442\u043e\u0432\u044b\u0439 \u043a\u0440\u0430\u0442\u0435\u0440"),
            Map.entry("netherval", "\u0410\u0434\u0441\u043a\u0430\u044f \u0434\u043e\u043b\u0438\u043d\u0430"),
            Map.entry("magma", "\u041c\u0430\u0433\u043c\u043e\u0432\u0430\u044f \u043b\u0430\u0433\u0443\u043d\u0430"),
            Map.entry("endwharf", "\u0420\u0430\u0437\u0440\u0443\u0448\u0435\u043d\u043d\u044b\u0439 \u043f\u0438\u0440\u0441"),
            Map.entry("silence", "\u041f\u0443\u0441\u0442\u043e\u0442\u043d\u044b\u0439 \u043b\u0435\u0441"),
            Map.entry("crystal", "\u0423\u0449\u0435\u043b\u044c\u0435 \u0440\u0430\u0437\u0440\u0443\u0448\u0438\u0442\u0435\u043b\u044f")
    );

    private static final Map<String, String> DUNGEON_NAMES = Map.of(
            "pyramid", "\u041f\u0438\u0440\u0430\u043c\u0438\u0434\u0430",
            "catacombs", "\u041a\u0430\u0442\u0430\u043a\u043e\u043c\u0431\u044b",
            "forest", "\u0414\u0440\u0435\u043c\u0443\u0447\u0438\u0439 \u043b\u0435\u0441",
            "nether", "\u0410\u0434\u0441\u043a\u0438\u0435 \u043d\u0435\u0434\u0440\u0430"
    );

    private static final Map<String, String> PROCEDURAL_DUNGEON_NAMES = Map.of(
            "forest", "\u041b\u0435\u0441",
            "camp", "\u041b\u0430\u0433\u0435\u0440\u044c \u0440\u0430\u0437\u0431\u043e\u0439\u043d\u0438\u043a\u043e\u0432",
            "fort", "\u0420\u0443\u0438\u043d\u044b \u043a\u0440\u0435\u043f\u043e\u0441\u0442\u0438",
            "town", "\u042d\u043d\u0434\u0435\u0440 \u0433\u043e\u0440\u043e\u0434"
    );

    private static final Set<String> SPAWN_IDS = Set.of(
            "spawn",
            "spawn_overworld",
            "spawn_nether",
            "spawn_end",
            "wand",
            "wand_overworld",
            "wand_nether",
            "wand_end",
            "miner",
            "craft"
    );

    public DwGameLocation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Game location id must not be blank");
        }
        id = id.trim();
    }

    public String normalizedId() {
        return id.toLowerCase(Locale.ROOT);
    }

    public boolean isEliteShaft() {
        return normalizedId().startsWith("shaft_elite");
    }

    public boolean isClanShaft() {
        return !isEliteShaft() && normalizedId().startsWith("shaft_clan");
    }

    public boolean isShaft() {
        return isEliteShaft() || isClanShaft() || normalizedId().startsWith("shaft");
    }

    public int shaftLevel() {
        if (!isShaft()) {
            return -1;
        }

        int separator = normalizedId().indexOf("shaft_");
        if (separator < 0) {
            return -1;
        }

        try {
            return Integer.parseInt(normalizedId().substring(separator + "shaft_".length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public boolean isBoss() {
        return normalizedId().startsWith("boss");
    }

    public String bossId() {
        if (!isBoss() || id.length() <= 5) {
            return null;
        }
        return id.substring(5);
    }

    public boolean isProceduralDungeon() {
        return normalizedId().startsWith("procedural_dungeon");
    }

    public boolean isDungeon() {
        return !isProceduralDungeon() && normalizedId().startsWith("dungeon");
    }

    public boolean isAnyDungeon() {
        return isDungeon() || isProceduralDungeon();
    }

    public String dungeonId() {
        String normalized = normalizedId();
        if (isDungeon()) {
            return normalized.startsWith("dungeon_") ? normalized.substring("dungeon_".length()) : normalized;
        }
        if (isProceduralDungeon()) {
            return normalized.startsWith("procedural_dungeon_")
                    ? normalized.substring("procedural_dungeon_".length())
                    : normalized;
        }
        return null;
    }

    public boolean isWarp() {
        return !isShaft() && !isBoss() && !isDungeon() && !isProceduralDungeon();
    }

    public boolean isFishing() {
        return isWarp() && FISHING_SPOT_IDS.contains(normalizedId());
    }

    public boolean isAlchemy() {
        return normalizedId().equals("alchemy");
    }

    public boolean isSiege() {
        return normalizedId().equals("siege");
    }

    public boolean isSpawn() {
        return SPAWN_IDS.contains(normalizedId());
    }

    public boolean isMine() {
        return isShaft() || normalizedId().equals("mine");
    }

    public String displayName() {
        if (isEliteShaft()) {
            return "\u042d\u043b\u0438\u0442\u043d\u0430\u044f \u0448\u0430\u0445\u0442\u0430";
        }
        if (isClanShaft()) {
            return "\u041a\u043b\u0430\u043d\u043e\u0432\u0430\u044f \u0448\u0430\u0445\u0442\u0430";
        }
        if (isShaft()) {
            int level = shaftLevel();
            return level >= 0 ? "\u0428\u0430\u0445\u0442\u0430 " + level + " \u0443\u0440." : "\u0428\u0430\u0445\u0442\u0430";
        }
        if (isDungeon() || isProceduralDungeon()) {
            String dungeonId = dungeonId();
            Map<String, String> names = isProceduralDungeon() ? PROCEDURAL_DUNGEON_NAMES : DUNGEON_NAMES;
            return "\u0414\u0430\u043d\u0436: " + names.getOrDefault(dungeonId, dungeonId != null ? dungeonId : id);
        }
        if (isFishing()) {
            return FISHING_SPOT_NAMES.getOrDefault(normalizedId(), id);
        }
        return LOCATION_NAMES.getOrDefault(normalizedId(), id);
    }
}
