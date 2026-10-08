package ru.wilyfox.boss;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import ru.wilyfox.client.protocol.BossTypeCatalog;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.protocol.DwBossType;
import ru.wilyfox.utils.BossLevel;
import ru.wilyfox.utils.BossName;
import ru.wilyfox.utils.Formatting;

import java.time.Instant;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.util.Set;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

public class BossTracker {
    private final Set<Integer> pendingEntityIds = new HashSet<>();
    // Keep only name/registry snapshots, never entities or old worlds. Decorations often keep
    // an unrelated custom name for their entire lifetime; do not parse it on every tick.
    private final Map<Integer, CheckedName> checkedNames = new HashMap<>();
    private final BossRepository repository;
    private ClientLevel trackedWorld;

    private String pendingBossName = null;
    private Long pendingBossTimeMillis = null;

    public BossTracker(BossRepository r) {
        this.repository = r;
    }

    public void onEntityLoad(Entity entity) {
        if (entity.level() instanceof ClientLevel world) observeWorld(world);
        checkedNames.remove(entity.getId());
        pendingEntityIds.add(entity.getId());
    }

    public void reset() {
        pendingEntityIds.clear();
        checkedNames.clear();
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
        long catalogRevision = BossTypeCatalog.revision();
        List<DwBossType> catalog = null;

        while (it.hasNext()) {
            Integer id = it.next();
            Entity entity = world.getEntity(id);

            if (entity == null) {
                it.remove();
                checkedNames.remove(id);
                continue;
            }

            Component customName = entity.getCustomName();
            if (customName == null) {
                checkedNames.remove(id);
                continue;
            }

            CheckedName previous = checkedNames.get(id);
            if (previous != null && previous.catalogRevision() == catalogRevision && previous.name().equals(customName)) {
                continue;
            }
            String raw = customName.getString();
            String bossName = BossName.getBossName(raw);
            if (bossName == null) {
                if (catalog == null) catalog = BossTypeCatalog.snapshot();
                bossName = BossName.resolveRegistryName(raw, catalog);
            }
            if (bossName != null) {
                pendingBossName = bossName;
                it.remove();
                checkedNames.remove(id);
                tryCommit();
                continue;
            }

            // Name sanitization removes punctuation, including the colons in HH:MM:SS.
            long millis = Formatting.parseTimeToMillis(raw);
            if (millis != -1) {
                pendingBossTimeMillis = millis;
                it.remove();
                checkedNames.remove(id);
                tryCommit();
            } else {
                checkedNames.put(id, new CheckedName(snapshotName(customName), catalogRevision));
            }
        }
    }

    private record CheckedName(Component name, long catalogRevision) {}

    private static Component snapshotName(Component name) {
        var copy = name.copy();
        // Preserve detection even if another mod mutates a sibling in place instead of
        // replacing the custom-name component with a new metadata packet.
        for (int i = 0; i < copy.getSiblings().size(); i++) {
            copy.getSiblings().set(i, snapshotName(copy.getSiblings().get(i)));
        }
        return copy;
    }

    private void tryCommit() {
        if (pendingBossName != null && pendingBossTimeMillis != null) {
            info(LOGGER, "COMMIT boss={}, time={}", pendingBossName, pendingBossTimeMillis);

            int level = java.util.Objects.requireNonNullElse(BossLevel.getBossLevel(pendingBossName), 0);
            DwBossType type = BossTypeCatalog.resolve(new BossInfo(pendingBossName, 0L, level));
            long duration = BossTimerMath.adjustDurationMillis(pendingBossTimeMillis,
                    DiamondWorldProtocolClient.isMythicalEventActive(), type != null && type.raid());
            long respawnAt = Instant.now().toEpochMilli() + duration;
            repository.upsert(pendingBossName, respawnAt);

            info(LOGGER, "Bosses size={}", repository.getAll().size());

            pendingBossName = null;
            pendingBossTimeMillis = null;
        }
    }
}
