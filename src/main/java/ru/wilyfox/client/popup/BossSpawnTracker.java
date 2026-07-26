package ru.wilyfox.client.popup;

import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class BossSpawnTracker {
    private static final long SPAWN_WINDOW_MS = 2_000L;
    private static final long NEW_CYCLE_THRESHOLD_MS = SPAWN_WINDOW_MS + 5_000L;

    private final Set<String> announcedBosses = new HashSet<>();

    List<BossInfo> update(Collection<BossInfo> bosses, long now) {
        if (bosses == null || bosses.isEmpty()) {
            return List.of();
        }

        List<BossInfo> spawned = new ArrayList<>();
        for (BossInfo boss : bosses) {
            if (boss == null) {
                continue;
            }

            String key = bossKey(boss);
            long remaining = boss.getRespawnAt() - now;
            if (remaining > NEW_CYCLE_THRESHOLD_MS) {
                announcedBosses.remove(key);
                continue;
            }

            if (remaining <= 0L
                    && remaining >= -SPAWN_WINDOW_MS
                    && announcedBosses.add(key)) {
                spawned.add(boss);
            }
        }
        return List.copyOf(spawned);
    }

    void reset() {
        announcedBosses.clear();
    }

    private static String bossKey(BossInfo boss) {
        String name = boss.getName() == null
                ? ""
                : Formatting.stripMinecraftFormatting(boss.getName()).trim().toLowerCase(Locale.ROOT);
        return !name.isBlank() ? name : "level:" + boss.getLevel();
    }
}
