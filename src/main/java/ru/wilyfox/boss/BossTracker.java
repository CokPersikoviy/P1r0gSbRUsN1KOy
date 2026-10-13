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
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

public class BossTracker {
    // Live candidates are removed on unload/reset. Avoid looking up every decorative entity
    // in the world's entity index each tick, and keep the last rejected name alongside it.
    // HashMap keeps the ID-set's traversal order used to pair adjacent name/time holograms.
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
        checkedNames.put(entity.getId(), new CheckedName(entity));
    }

    public void onEntityUnload(Entity entity) {
        CheckedName candidate = checkedNames.get(entity.getId());
        if (candidate != null && candidate.entity == entity) checkedNames.remove(entity.getId());
    }

    public void reset() {
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
        var it = checkedNames.values().iterator();
        long catalogRevision = BossTypeCatalog.revision();
        List<DwBossType> catalog = null;

        while (it.hasNext()) {
            CheckedName previous = it.next();
            Entity entity = previous.entity;

            if (entity.isRemoved() || entity.level() != world) {
                it.remove();
                continue;
            }

            Component customName = entity.getCustomName();
            if (customName == null) {
                previous.name = null;
                continue;
            }

            if (previous.name != null && previous.catalogRevision == catalogRevision && previous.name.equals(customName)) {
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
                tryCommit();
                continue;
            }

            // Name sanitization removes punctuation, including the colons in HH:MM:SS.
            long millis = Formatting.parseTimeToMillis(raw);
            if (millis != -1) {
                pendingBossTimeMillis = millis;
                it.remove();
                tryCommit();
            } else {
                previous.name = snapshotName(customName);
                previous.catalogRevision = catalogRevision;
            }
        }
    }

    private static final class CheckedName {
        private final Entity entity;
        private Component name;
        private long catalogRevision;
        private CheckedName(Entity entity) { this.entity = entity; }
    }

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
