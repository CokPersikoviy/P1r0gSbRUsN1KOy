package ru.wilyfox.client.protocol;

import org.junit.jupiter.api.Test;
import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.boss.BossRepository;

import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossTimerProtocolCompatibilityTest {
    @Test
    void onlyMythicalRaidDurationIsAdjustedAtReceiptAndItsDeadlineStaysFixed() {
        ProtocolState state = new ProtocolState();
        RecordingRepository repository = new RecordingRepository();
        state.bossRepository = repository;
        state.currentGameEvent = DwGameEvent.MYTHICAL_EVENT;
        state.bossTypes.put("raid", new DwBossType("raid", "Raid", "STONE", 500, 1, 0, true));
        state.bossTypes.put("normal", new DwBossType("normal", "Normal", "STONE", 35, 2, 0, false));
        long before = System.currentTimeMillis();

        ProtocolPayloadHandlers.applyBossTimers(state,
                new DwBossTimersPacket(Map.of("raid", 15_000L, "normal", 15_000L, "unknown", 15_000L)));

        long after = System.currentTimeMillis();
        assertDeadline(repository.snapshot.get("raid"), before + 10_000L, after + 10_000L);
        assertDeadline(repository.snapshot.get("normal"), before + 15_000L, after + 15_000L);
        assertDeadline(repository.snapshot.get("unknown"), before + 15_000L, after + 15_000L);
        long fixedDeadline = repository.snapshot.get("raid").getRespawnAt();
        state.currentGameEvent = DwGameEvent.NONE;
        assertEquals(fixedDeadline, repository.snapshot.get("raid").getRespawnAt());

        before = System.currentTimeMillis();
        ProtocolPayloadHandlers.applyBossTimers(state, new DwBossTimersPacket(Map.of("raid", 15_000L)));
        after = System.currentTimeMillis();
        assertDeadline(repository.snapshot.get("raid"), before + 15_000L, after + 15_000L);
    }

    private static void assertDeadline(BossInfo boss, long earliest, long latest) {
        assertTrue(boss.getRespawnAt() >= earliest && boss.getRespawnAt() <= latest);
    }

    @Test
    void staleRawTimerDoesNotOverwriteExistingDeadlineEvenIfMythicalDivisionWouldFitGrace() {
        ProtocolState state = new ProtocolState();
        RecordingRepository repository = new RecordingRepository();
        state.bossRepository = repository;
        state.currentGameEvent = DwGameEvent.MYTHICAL_EVENT;
        state.bossTypes.put("raid", new DwBossType("raid", "Raid", "STONE", 500, 1, 0, true));
        ProtocolPayloadHandlers.applyBossTimers(state, new DwBossTimersPacket(Map.of("raid", 15_000L)));
        long deadline = repository.snapshot.get("raid").getRespawnAt();
        ProtocolPayloadHandlers.applyBossTimers(state, new DwBossTimersPacket(Map.of("raid", -40_000L)));
        ProtocolPayloadHandlers.applyBossTimers(state, new DwBossTimersPacket(Map.of()));
        assertEquals(deadline, repository.snapshot.get("raid").getRespawnAt());
    }

    private static final class RecordingRepository extends BossRepository {
        Map<String, BossInfo> snapshot = new LinkedHashMap<>();

        @Override
        public boolean acceptsProtocolDuration(long remainingMillis) {
            return remainingMillis > -30_000L;
        }

        @Override
        public void mergeProtocol(Map<String, BossInfo> bosses) {
            snapshot.putAll(bosses);
        }
    }
}
