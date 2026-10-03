package ru.wilyfox.boss;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import ru.wilyfox.utils.BossName;
import ru.wilyfox.utils.Formatting;

import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

public class BossTracker {
    private final Set<Integer> pendingEntityIds = new HashSet<>();
    private final BossRepository repository;
    private ClientLevel trackedWorld;

    private String pendingBossName = null;
    private Long pendingBossTimeMillis = null;

    public BossTracker(BossRepository r) {
        this.repository = r;
    }

    public void onEntityLoad(Entity entity) {
        if (entity.level() instanceof ClientLevel world) observeWorld(world);
        pendingEntityIds.add(entity.getId());
    }

    public void reset() {
        pendingEntityIds.clear();
        pendingBossName = null;
        pendingBossTimeMillis = null;
        trackedWorld = null;
    }

    private void observeWorld(ClientLevel world) {
        if (trackedWorld != world) {
            reset();
            trackedWorld = world;
        }
    }

    public void onWorldTick(ClientLevel world) {
        observeWorld(world);
        Iterator<Integer> it = pendingEntityIds.iterator();

        while (it.hasNext()) {
            int id = it.next();
            Entity entity = world.getEntity(id);

            if (entity == null) {
                it.remove();
                continue;
            }

            if (entity.getCustomName() == null) {
                continue;
            }

            String raw = entity.getCustomName().getString();
            String clean = Formatting.sanitize(raw);

            String bossName = BossName.getBossName(clean);
            if (bossName != null) {
                pendingBossName = bossName;
                it.remove();
                tryCommit();
                continue;
            }

            // Name sanitization removes punctuation, including the colons in HH:MM:SS.
            long millis = Formatting.parseTimeToMillis(raw);
            if (millis != -1) {
                pendingBossTimeMillis = millis;
                it.remove();
                tryCommit();
            }
        }
    }

    private void tryCommit() {
        if (pendingBossName != null && pendingBossTimeMillis != null) {
            info(LOGGER, "COMMIT boss={}, time={}", pendingBossName, pendingBossTimeMillis);

            long respawnAt = Instant.now().toEpochMilli() + pendingBossTimeMillis;
            respawnAt = ((respawnAt + 999) / 1000) * 1000;
            repository.upsert(pendingBossName, respawnAt);

            info(LOGGER, "Bosses size={}", repository.getAll().size());

            pendingBossName = null;
            pendingBossTimeMillis = null;
        }
    }
}
