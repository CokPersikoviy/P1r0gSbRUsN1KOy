package ru.wilyfox.boss;

import net.minecraft.world.item.ItemStack;
import ru.wilyfox.client.hud.config.BossWidgetConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.utils.BossLevel;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import static ru.wilyfox.utils.Formatting.stripMinecraftFormatting;

public class BossRepository {
    private final Map<String, BossInfo> worldBosses = new LinkedHashMap<>();
    private final Map<String, BossInfo> protocolBosses = new LinkedHashMap<>();
    private final Map<String, Integer> protocolLevelsByName = new LinkedHashMap<>();
    private final Map<String, ItemStack> discoveredBossIcons = new LinkedHashMap<>();
    private final LongSupplier clock;
    private final LongSupplier spawnGraceSupplier;

    public BossRepository() {
        this(System::currentTimeMillis, BossRepository::configuredSpawnGraceMs);
    }

    BossRepository(LongSupplier clock, LongSupplier spawnGraceSupplier) {
        this.clock = Objects.requireNonNull(clock);
        this.spawnGraceSupplier = Objects.requireNonNull(spawnGraceSupplier);
    }

    public void upsert(String bossName, long respawnAtMillis) {
        int fallbackLevel = Objects.requireNonNullElse(BossLevel.getBossLevel(bossName), 0);
        int level = protocolLevelsByName.getOrDefault(nameKey(bossName), fallbackLevel);
        upsert(worldBosses, bossName, null, bossName, respawnAtMillis, level);
    }

    public void upsertProtocol(String bossId, String bossName, long respawnAtMillis, int level) {
        rememberProtocolLevel(bossName, level);
        upsert(protocolBosses, bossId, bossId, bossName, respawnAtMillis, level);
    }

    public void updateProtocolMetadata(String bossId, String bossName, int level) {
        rememberProtocolLevel(bossName, level);
        protocolBosses.computeIfPresent(bossId, (ignored, boss) ->
                new BossInfo(bossId, bossName, boss.getRespawnAt(), level));
        worldBosses.replaceAll((ignored, boss) -> sameName(boss.getName(), bossName)
                ? new BossInfo(boss.getId(), bossName, boss.getRespawnAt(), level)
                : boss);
    }

    public void clearProtocolMetadata() {
        protocolLevelsByName.clear();
    }

    public void clearProtocol() {
        protocolBosses.clear();
        protocolLevelsByName.clear();
        discoveredBossIcons.clear();
    }

    public Collection<BossInfo> getAll() {
        return getAllMerged();
    }

    public Collection<BossInfo> getAllWorld() {
        return worldBosses.values().stream()
                .sorted(Comparator.comparingLong(BossInfo::getRespawnAt))
                .collect(Collectors.toList());
    }

    public Collection<BossInfo> getAllProtocol() {
        cleanupProtocol();
        return deduplicateByName(protocolBosses.values()).stream()
                .sorted(Comparator.comparingLong(BossInfo::getRespawnAt))
                .collect(Collectors.toList());
    }

    public Collection<BossInfo> getAllMerged() {
        cleanupProtocol();
        Map<String, BossInfo> merged = new LinkedHashMap<>();
        Set<Integer> protocolLevels = new HashSet<>();

        for (BossInfo boss : deduplicateByName(protocolBosses.values())) {
            merged.put(nameKey(boss.getName()), boss);
            if (boss.getLevel() > 0) {
                protocolLevels.add(boss.getLevel());
            }
        }

        for (BossInfo boss : worldBosses.values()) {
            String nameKey = nameKey(boss.getName());
            if (merged.containsKey(nameKey)
                    || boss.getLevel() > 0 && protocolLevels.contains(boss.getLevel())) {
                continue;
            }
            merged.putIfAbsent(nameKey, boss);
        }

        return merged.values().stream()
                .sorted(Comparator.comparingLong(BossInfo::getRespawnAt))
                .collect(Collectors.toList());
    }

    public void mergeProtocol(Map<String, BossInfo> bosses) {
        // Both reference versions apply individual timer updates. Missing entries, including
        // an empty packet, do not cancel live timers; expiration uses the HUD's grace policy.
        protocolBosses.putAll(bosses);
        bosses.values().forEach(boss -> rememberProtocolLevel(boss.getName(), boss.getLevel()));
        cleanupProtocol();
    }

    /** Filter the server duration before mythical scaling or overwriting an existing timer. */
    public boolean acceptsProtocolDuration(long remainingMillis) {
        long grace = spawnGraceMs();
        return remainingMillis > 0L || grace < 0L || remainingMillis > -grace;
    }

    // How long a just-spawned boss is retained, matching the boss
    // widget's post-spawn settings. -1 = keep until it respawns (a new future timer arrives).
    private static long configuredSpawnGraceMs() {
        BossWidgetConfig config = ConfigManager.get().bossWidget;
        if (config.showSpawnedUntilKilled) {
            return -1L;
        }
        return Math.max(0, config.postSpawnShowSeconds) * 1000L;
    }

    private long spawnGraceMs() {
        return spawnGraceSupplier.getAsLong();
    }

    private void cleanupProtocol() {
        long grace = spawnGraceMs();
        if (grace < 0L) {
            return;
        }

        long cutoff = clock.getAsLong() - grace;
        protocolBosses.entrySet().removeIf(entry -> entry.getValue().getRespawnAt() < cutoff);
    }

    public ItemStack getDiscoveredIcon(BossInfo boss) {
        if (boss.getLevel() > 0) {
            ItemStack byLevel = discoveredBossIcons.get(levelKey(boss.getLevel()));
            if (byLevel != null) {
                return byLevel.copy();
            }
        }

        ItemStack byName = discoveredBossIcons.get(iconKey(boss.getName()));
        return byName != null ? byName.copy() : null;
    }

    public void rememberDiscoveredIcon(String bossName, int level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        ItemStack icon = stack.copy();
        icon.setCount(1);

        if (bossName != null && !bossName.isBlank()) {
            discoveredBossIcons.put(iconKey(bossName), icon.copy());
        }

        if (level > 0) {
            discoveredBossIcons.put(levelKey(level), icon.copy());
        }
    }

    public String findBossNameByLevel(int level) {
        for (BossInfo boss : protocolBosses.values()) {
            if (boss.getLevel() == level) {
                return boss.getName();
            }
        }

        for (BossInfo boss : worldBosses.values()) {
            if (boss.getLevel() == level) {
                return boss.getName();
            }
        }

        return null;
    }

    private void upsert(
            Map<String, BossInfo> storage,
            String key,
            String bossId,
            String bossName,
            long respawnAtMillis,
            int level
    ) {
        storage.compute(key, (ignored, oldBoss) -> {
            if (oldBoss == null
                    || oldBoss.getLevel() != level
                    || !Objects.equals(oldBoss.getId(), bossId)
                    || !Objects.equals(oldBoss.getName(), bossName)) {
                return new BossInfo(bossId, bossName, respawnAtMillis, level);
            }

            oldBoss.setRespawnAt(respawnAtMillis);
            return oldBoss;
        });
    }

    private Collection<BossInfo> deduplicateByName(Collection<BossInfo> bosses) {
        Map<String, BossInfo> unique = new LinkedHashMap<>();
        for (BossInfo boss : bosses) {
            unique.merge(nameKey(boss.getName()), boss, (current, candidate) ->
                    candidate.getRespawnAt() > current.getRespawnAt() ? candidate : current);
        }
        return unique.values();
    }

    private void rememberProtocolLevel(String bossName, int level) {
        if (bossName != null && !bossName.isBlank() && level > 0) {
            protocolLevelsByName.put(nameKey(bossName), level);
        }
    }

    private boolean sameName(String left, String right) {
        return nameKey(left).equals(nameKey(right));
    }

    private String nameKey(String bossName) {
        if (bossName == null) {
            return "";
        }
        return stripMinecraftFormatting(bossName).trim().toLowerCase(Locale.ROOT);
    }

    private String levelKey(int level) {
        return "level:" + level;
    }

    private String iconKey(String bossName) {
        return "name:" + nameKey(bossName);
    }
}
