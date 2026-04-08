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

    private String pendingBossName = null;
    private Long pendingBossTimeMillis = null;

    public BossTracker(BossRepository r) {
        this.repository = r;
    }

    public void onEntityLoad(Entity entity) {
        pendingEntityIds.add(entity.getId());
    }

    public void onWorldTick(ClientLevel world) {
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

            if (BossName.getBossName(clean) != null) {
                pendingBossName = BossName.getBossName(clean);
                it.remove();
                tryCommit();
                continue;
            }

            long millis = Formatting.parseTimeToMillis(clean);
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
