package ru.wilyfox.boss;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BossRepositoryTest {
    private static final long GRACE_MS = 30_000L;

    @Test
    void protocolBossReplacesLocalBossWithSameNameAndOldLevel() {
        long future = System.currentTimeMillis() + 60_000L;
        BossRepository repository = repository();

        repository.upsert("Хранитель", future);
        repository.upsertProtocol("keeper_v2", "Хранитель", future + 1_000L, 510);

        Collection<BossInfo> bosses = repository.getAllMerged();

        assertEquals(1, bosses.size());
        BossInfo boss = bosses.iterator().next();
        assertEquals("Хранитель", boss.getName());
        assertEquals(510, boss.getLevel());
        assertEquals(future + 1_000L, boss.getRespawnAt());
    }

    @Test
    void localBossUsesDynamicProtocolLevelInsteadOfStaticTable() {
        long future = System.currentTimeMillis() + 60_000L;
        BossRepository repository = repository();

        repository.updateProtocolMetadata("keeper_v2", "Хранитель", 510);
        repository.upsert("Хранитель", future);

        assertEquals(510, repository.getAllWorld().iterator().next().getLevel());
    }

    @Test
    void protocolSnapshotRemovesFutureEntryMissingFromNextPacket() {
        long now = System.currentTimeMillis();
        BossRepository repository = repository();
        repository.upsertProtocol("old_id", "Old Boss", now + 60_000L, 100);

        repository.replaceProtocol(Map.of(
                "new_id", new BossInfo("New Boss", now + 90_000L, 110)
        ));

        Collection<BossInfo> bosses = repository.getAllProtocol();
        assertEquals(1, bosses.size());
        assertEquals("New Boss", bosses.iterator().next().getName());
    }

    @Test
    void protocolOnlyViewDeduplicatesSameNameAcrossDifferentLevels() {
        long future = System.currentTimeMillis() + 60_000L;
        BossRepository repository = repository();
        repository.upsertProtocol("keeper_old", "Хранитель", future, 500);
        repository.upsertProtocol("keeper_new", "Хранитель", future + 1_000L, 510);

        Collection<BossInfo> bosses = repository.getAllProtocol();

        assertEquals(1, bosses.size());
        assertEquals(510, bosses.iterator().next().getLevel());
    }

    @Test
    void protocolSnapshotRetainsJustSpawnedEntryDuringGracePeriod() {
        long now = System.currentTimeMillis();
        BossRepository repository = repository();
        repository.upsertProtocol("spawned", "Spawned Boss", now - 1_000L, 120);

        repository.replaceProtocol(Map.of());

        assertEquals(1, repository.getAllProtocol().size());
    }

    @Test
    void currentTimerWinsOverRetainedSpawnedAliasWithSameName() {
        long now = System.currentTimeMillis();
        BossRepository repository = repository();
        repository.upsertProtocol("old_id", "Хранитель", now - 1_000L, 500);
        repository.replaceProtocol(Map.of(
                "new_id", new BossInfo("Хранитель", now + 60_000L, 510)
        ));

        Collection<BossInfo> bosses = repository.getAllProtocol();

        assertEquals(1, bosses.size());
        assertEquals(510, bosses.iterator().next().getLevel());
        assertEquals(now + 60_000L, bosses.iterator().next().getRespawnAt());
    }

    private static BossRepository repository() {
        return new BossRepository(System::currentTimeMillis, () -> GRACE_MS);
    }
}
