package ru.wilyfox.client.protocol;

import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.utils.BossLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static ru.wilyfox.utils.Formatting.stripMinecraftFormatting;

/** Boss type registry retained across server switches for settings screens and stable filtering. */
public final class BossTypeCatalog {
    private static final Map<String, DwBossType> TYPES = new LinkedHashMap<>();

    static {
        seedBuiltInTypes();
    }

    private BossTypeCatalog() {
    }

    public static synchronized void update(Map<String, DwBossType> types) {
        if (types == null) {
            return;
        }

        types.forEach((packetId, type) -> {
            if (type == null) {
                return;
            }

            String id = normalizeId(type.id() == null || type.id().isBlank() ? packetId : type.id());
            if (id.isEmpty()) {
                return;
            }

            DwBossType normalized = new DwBossType(
                    id,
                    type.name(),
                    type.material(),
                    type.level(),
                    type.customModelData(),
                    type.capturePoints(),
                    type.raid()
            );
            removeSupersededEntries(normalized);
            TYPES.put(id, normalized);
        });
    }

    public static synchronized void observe(String bossId, String bossName, int level) {
        String id = normalizeId(bossId);
        if (id.isEmpty()) {
            return;
        }

        DwBossType existing = TYPES.get(id);
        if (existing != null && existing.level() > 0 && existing.name() != null && !existing.name().isBlank()) {
            return;
        }

        DwBossType observed = new DwBossType(id, bossName, "", level, 0, 0, false);
        removeSupersededEntries(observed);
        TYPES.put(id, observed);
    }

    public static synchronized List<DwBossType> snapshot() {
        List<DwBossType> result = new ArrayList<>(TYPES.values());
        result.sort(Comparator
                .comparingInt((DwBossType type) -> type.level() > 0 ? type.level() : Integer.MAX_VALUE)
                .thenComparing(type -> normalizeName(type.name()))
                .thenComparing(DwBossType::id));
        return List.copyOf(result);
    }

    public static synchronized DwBossType resolve(BossInfo boss) {
        if (boss == null) {
            return null;
        }

        String id = normalizeId(boss.getId());
        if (!id.isEmpty()) {
            DwBossType exact = TYPES.get(id);
            if (exact != null) {
                return exact;
            }
        }

        if (boss.getLevel() > 0) {
            for (DwBossType type : TYPES.values()) {
                if (type.level() == boss.getLevel()) {
                    return type;
                }
            }
        }

        String name = normalizeName(boss.getName());
        if (!name.isEmpty()) {
            for (DwBossType type : TYPES.values()) {
                if (normalizeName(type.name()).equals(name)) {
                    return type;
                }
            }
        }
        return null;
    }

    public static String fallbackId(int level) {
        return "level:" + level;
    }

    public static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    static synchronized void resetForTesting() {
        TYPES.clear();
        seedBuiltInTypes();
    }

    private static void seedBuiltInTypes() {
        BossLevel.getKnownBosses().forEach((level, name) -> {
            String id = fallbackId(level);
            TYPES.put(id, new DwBossType(id, name, "", level, 0, 0, false));
        });
    }

    private static void removeSupersededEntries(DwBossType incoming) {
        String incomingName = normalizeName(incoming.name());
        TYPES.entrySet().removeIf(entry -> {
            DwBossType current = entry.getValue();
            if (entry.getKey().equals(incoming.id())) {
                return false;
            }
            if (incoming.level() > 0 && current.level() == incoming.level()) {
                return true;
            }
            return !incomingName.isEmpty() && normalizeName(current.name()).equals(incomingName);
        });
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return stripMinecraftFormatting(name).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
