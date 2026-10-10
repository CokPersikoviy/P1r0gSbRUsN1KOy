package ru.wilyfox.boss;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossRepositoryTest {
    @Test void sharedTimersAreVisibleInEverySourceModeAndNeverOverrideDirectObservations() {
        var now = new java.util.concurrent.atomic.AtomicLong(1_000);
        var repo = new BossRepository(now::get, () -> GRACE_MS);
        assertEquals(2, repo.importShared(java.util.List.of(new BossInfo("Remote", 5_000, 125), new BossInfo("Direct", 6_000, 130))));
        assertEquals(2, repo.getAllWorld().size()); assertEquals(2, repo.getAllProtocol().size()); assertEquals(2, repo.getAllMerged().size());
        repo.upsertProtocol("direct", "Direct", 7_000, 130);
        assertEquals(7_000, repo.getAllProtocol().stream().filter(b -> b.getName().equals("Direct")).findFirst().orElseThrow().getRespawnAt());
        assertEquals(2, repo.getAllMerged().size());
        now.set(5_001); assertEquals(1, repo.getAllWorld().size());
        repo.clearProtocol(); assertTrue(repo.getAllMerged().isEmpty());
    }
    private static final long GRACE_MS = 30_000L;

    @Test
    void serverLegionWithPlainIReplacesHologramLegionWithOldLevel() {
        long future = System.currentTimeMillis() + 60_000L;
        BossRepository repository = repository();
        repository.upsert("Бессмертный Легион", future);
        repository.upsertProtocol("ImmortalLegion", "Бессмертныи легион", future, 130);
        assertEquals(1, repository.getAllMerged().size());
        BossInfo boss = repository.getAllMerged().iterator().next();
        assertEquals("ImmortalLegion", boss.getId());
        assertEquals(130, boss.getLevel());
        assertEquals(future, boss.getRespawnAt());
    }

    @Test
    void protocolAliasesDeduplicateWithoutLosingTheCurrentTimer() {
        long future = System.currentTimeMillis() + 60_000L;
        BossRepository repository = repository();
        repository.upsertProtocol("legion_old", "§bБессмертный Легион", future, 105);
        repository.upsertProtocol("legion_new", "Бессмертныи\u00a0легион", future + 1_000L, 130);
        assertEquals(1, repository.getAllProtocol().size());
        assertEquals("legion_new", repository.getAllProtocol().iterator().next().getId());
    }

    @Test
    void worldOnlyUsesOneIdentityForLegionAliasesButKeepsDistinctUnknownBosses() {
        long future = System.currentTimeMillis() + 60_000L;
        BossRepository repository = repository();
        repository.upsert("Бессмертныи легион", future);
        repository.upsert("Бессмертный Легион", future + 1_000L);
        repository.upsert("Командир Легиона", future + 2_000L);
        assertEquals(1, repository.getAllWorld().size());
        assertEquals(future + 2_000L, repository.getAllWorld().iterator().next().getRespawnAt());
        repository.upsert("Unknown Boss 1", future);
        repository.upsert("Unknown Boss 2", future);
        assertEquals(3, repository.getAllWorld().size());
    }

    @Test
    void dynamicLegionLevelMatchesTheHologramDespiteDifferentSpelling() {
        long future = System.currentTimeMillis() + 60_000L;
        BossRepository repository = repository();
        repository.updateProtocolMetadata("ImmortalLegion", "Бессмертныи легион", 130);
        repository.upsert("Бессмертный Легион", future);
        assertEquals(130, repository.getAllWorld().iterator().next().getLevel());
        repository.updateProtocolMetadata("ImmortalLegion", "Бессмертныи легион", 140);
        assertEquals(140, repository.getAllWorld().iterator().next().getLevel());
        assertEquals(future, repository.getAllWorld().iterator().next().getRespawnAt());
    }

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
    void protocolBossRetainsStableTypeId() {
        BossRepository repository = repository();
        repository.upsertProtocol("keeper_v2", "Хранитель", System.currentTimeMillis() + 60_000L, 510);

        assertEquals("keeper_v2", repository.getAllProtocol().iterator().next().getId());
    }

    @Test
    void partialProtocolPacketPreservesFutureEntriesAndEmptyPacketDoesNotCancelThem() {
        long now = System.currentTimeMillis();
        BossRepository repository = repository();
        repository.upsertProtocol("old_id", "Old Boss", now + 60_000L, 100);

        repository.mergeProtocol(Map.of(
                "new_id", new BossInfo("New Boss", now + 90_000L, 110)
        ));
        repository.mergeProtocol(Map.of());

        Collection<BossInfo> bosses = repository.getAllProtocol();
        assertEquals(2, bosses.size());
        assertEquals(java.util.Set.of("Old Boss", "New Boss"),
                bosses.stream().map(BossInfo::getName).collect(java.util.stream.Collectors.toSet()));
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
    void protocolUpdateRetainsJustSpawnedEntryDuringGracePeriod() {
        long now = System.currentTimeMillis();
        BossRepository repository = repository();
        repository.upsertProtocol("spawned", "Spawned Boss", now - 1_000L, 120);

        repository.mergeProtocol(Map.of());

        assertEquals(1, repository.getAllProtocol().size());
    }

    @Test
    void currentTimerWinsOverRetainedSpawnedAliasWithSameName() {
        long now = System.currentTimeMillis();
        BossRepository repository = repository();
        repository.upsertProtocol("old_id", "Хранитель", now - 1_000L, 500);
        repository.mergeProtocol(Map.of(
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

    @Test
    void emptyProtocolPacketExpiresTimersOnlyAfterConfiguredGrace() {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(100_000L);
        BossRepository repository = new BossRepository(clock::get, () -> GRACE_MS);
        repository.upsertProtocol("boss", "Boss", 100_000L, 100);
        clock.set(129_999L);
        repository.mergeProtocol(Map.of());
        assertEquals(1, repository.getAllProtocol().size());
        clock.set(130_001L);
        repository.mergeProtocol(Map.of());
        assertEquals(0, repository.getAllProtocol().size());
    }

    @Test
    void protocolDurationGraceHasStrictBoundaryAndUnlimitedModeAcceptsAll() {
        BossRepository repository = repository();
        assertTrue(repository.acceptsProtocolDuration(60_000L));
        assertTrue(repository.acceptsProtocolDuration(0L));
        assertTrue(repository.acceptsProtocolDuration(-29_999L));
        assertFalse(repository.acceptsProtocolDuration(-30_000L));
        assertFalse(repository.acceptsProtocolDuration(Long.MIN_VALUE));
        assertTrue(new BossRepository(() -> 0L, () -> -1L).acceptsProtocolDuration(Long.MIN_VALUE));
    }
    @Test
    void unchangedReadsReuseViewsAndDeadlineMutationReordersThem() {
        BossRepository repository = new BossRepository(() -> 0L, () -> -1L);
        repository.upsert("World", 40_000L);
        repository.upsertProtocol("first", "First", 10_000L, 1);
        repository.upsertProtocol("second", "Second", 20_000L, 2);
        var protocol = repository.getAllProtocol();
        var merged = repository.getAllMerged();
        var world = repository.getAllWorld();
        assertSame(protocol, repository.getAllProtocol());
        assertSame(merged, repository.getAllMerged());
        assertSame(world, repository.getAllWorld());
        protocol.iterator().next().setRespawnAt(30_000L);
        assertNotSame(protocol, repository.getAllProtocol());
        assertEquals("second", repository.getAllProtocol().iterator().next().getId());
        assertThrows(UnsupportedOperationException.class, () -> repository.getAllMerged().clear());
    }

    @Test
    void cachedViewsDetectSameSizeReplacementsRenamesAndAliasWinnerChanges() {
        BossRepository repository = new BossRepository(() -> 0L, () -> -1L);
        BossInfo older = new BossInfo("old", "Бессмертный Легион", 10_000L, 130);
        BossInfo newer = new BossInfo("new", "Бессмертныи легион", 20_000L, 130);
        repository.mergeProtocol(Map.of("old", older, "new", newer));
        assertSame(newer, repository.getAllProtocol().iterator().next());
        older.setRespawnAt(30_000L);
        assertSame(older, repository.getAllProtocol().iterator().next());
        repository.updateProtocolMetadata("new", "Different Boss", 200);
        assertEquals(2, repository.getAllProtocol().size());
        var before = repository.getAllProtocol();
        repository.mergeProtocol(Map.of("new", new BossInfo("new", "Replacement", 40_000L, 210)));
        assertNotSame(before, repository.getAllProtocol());
        assertTrue(repository.getAllProtocol().stream().anyMatch(boss -> boss.getName().equals("Replacement")));
    }

    @Test
    void cachedProtocolExpiresOnReadAndClearRestoresWorldFallback() {
        var now = new java.util.concurrent.atomic.AtomicLong(100_000L);
        var grace = new java.util.concurrent.atomic.AtomicLong(GRACE_MS);
        BossRepository repository = new BossRepository(now::get, grace::get);
        repository.upsert("Кригер", 180_000L);
        repository.upsertProtocol("krieger", "Кригер", 100_000L, 5);
        assertEquals("krieger", repository.getAllMerged().iterator().next().getId());
        now.set(130_000L);
        assertEquals(1, repository.getAllProtocol().size());
        now.incrementAndGet();
        assertEquals(0, repository.getAllProtocol().size());
        assertNull(repository.getAllMerged().iterator().next().getId());
        grace.set(-1L);
        repository.upsertProtocol("krieger", "Кригер", 100_000L, 5);
        now.set(1_000_000L);
        assertEquals(1, repository.getAllProtocol().size());
        repository.clearProtocol();
        assertEquals(0, repository.getAllProtocol().size());
        assertEquals(1, repository.getAllMerged().size());
    }

    @Test
    void repeatedReadsAvoidNameParsingAndLargeAllocationBursts() {
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var allocation = (com.sun.management.ThreadMXBean) bean;
        org.junit.jupiter.api.Assumptions.assumeTrue(allocation.isThreadAllocatedMemorySupported());
        allocation.setThreadAllocatedMemoryEnabled(true);
        BossRepository repository = new BossRepository(() -> 0L, () -> -1L);
        for (int i = 0; i < 50; i++) {
            repository.upsertProtocol("boss-" + i, "Unknown Boss " + i, 100_000L + i, i + 1);
        }
        for (int i = 0; i < 2_000; i++) {
            repository.getAllWorld(); repository.getAllProtocol(); repository.getAllMerged();
        }
        long id = Thread.currentThread().threadId();
        long before = allocation.getThreadAllocatedBytes(id);
        for (int i = 0; i < 1_000; i++) {
            assertEquals(50, repository.getAllProtocol().size());
            assertEquals(50, repository.getAllMerged().size());
            assertEquals(0, repository.getAllWorld().size());
        }
        long bytes = allocation.getThreadAllocatedBytes(id) - before;
        System.out.println("Boss repository: 3,000 cached reads / 50 bosses allocated " + bytes + " bytes");
        assertTrue(bytes < 1_000_000L, "Cached reads allocated " + bytes + " bytes");
    }

}
