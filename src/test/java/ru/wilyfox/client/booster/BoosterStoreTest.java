package ru.wilyfox.client.booster;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoosterStoreTest {
    @Test
    void summaryAddsBonusAboveOneAndUsesLatestEnd() {
        BoosterStore store = new BoosterStore();
        store.replace(BoosterStore.Kind.MONEY, List.of(
                new BoosterStore.ProtocolEntry(1.5D, 10_000L),
                new BoosterStore.ProtocolEntry(2.0D, 20_000L)
        ));

        BoosterStore.Snapshot snapshot = store.getSnapshot(BoosterStore.Kind.MONEY);
        assertEquals(2.5D, snapshot.totalMultiplier());
        assertEquals(20_000L, snapshot.latest().durationMillis());
    }

    @Test
    void fullSnapshotUsesOneTimestampAndClearsMissingKinds() {
        MutableClocks clocks = new MutableClocks(1_000L, 5_000L);
        BoosterStore store = new BoosterStore(clocks::wallMillis, clocks::monotonicNanos);
        Map<BoosterStore.Kind, List<BoosterStore.ProtocolEntry>> snapshot = new EnumMap<>(BoosterStore.Kind.class);
        snapshot.put(BoosterStore.Kind.MONEY, List.of(new BoosterStore.ProtocolEntry(1.5D, 10_000L)));
        snapshot.put(BoosterStore.Kind.SHAFT, List.of(new BoosterStore.ProtocolEntry(2.0D, 5_000L)));

        store.replaceAll(snapshot);

        BoosterStore.Entry money = store.getSnapshot(BoosterStore.Kind.MONEY).entries().getFirst();
        BoosterStore.Entry shaft = store.getSnapshot(BoosterStore.Kind.SHAFT).entries().getFirst();
        assertEquals(1_000L, money.startedAt());
        assertEquals(money.startedAt(), shaft.startedAt());
        assertEquals(11_000L, money.endsAt());
        assertEquals(6_000L, shaft.endsAt());

        store.replaceAll(Map.of(
                BoosterStore.Kind.SHARD,
                List.of(new BoosterStore.ProtocolEntry(1.25D, 3_000L))
        ));

        assertFalse(store.getSnapshot(BoosterStore.Kind.MONEY).hasAny());
        assertFalse(store.getSnapshot(BoosterStore.Kind.SHAFT).hasAny());
        assertTrue(store.getSnapshot(BoosterStore.Kind.SHARD).hasAny());
    }

    @Test
    void remainingTimeUsesMonotonicClock() {
        MutableClocks clocks = new MutableClocks(10_000L, 0L);
        BoosterStore store = new BoosterStore(clocks::wallMillis, clocks::monotonicNanos);
        store.replace(BoosterStore.Kind.MONEY, List.of(new BoosterStore.ProtocolEntry(1.5D, 10_000L)));
        BoosterStore.Entry entry = store.getSnapshot(BoosterStore.Kind.MONEY).entries().getFirst();

        clocks.wallMillis += TimeUnit.HOURS.toMillis(6L);
        assertEquals(10_000L, entry.remainingMillis());

        clocks.monotonicNanos += TimeUnit.SECONDS.toNanos(4L);
        clocks.wallMillis -= TimeUnit.DAYS.toMillis(2L);
        assertEquals(6_000L, entry.remainingMillis());

        clocks.monotonicNanos += TimeUnit.SECONDS.toNanos(6L);
        assertFalse(store.hasAnyActive());
    }

    @Test
    void boostersExpireIndependentlyInSortedList() {
        MutableClocks clocks = new MutableClocks(0L, 0L);
        BoosterStore store = new BoosterStore(clocks::wallMillis, clocks::monotonicNanos);
        store.replace(BoosterStore.Kind.SHARD, List.of(
                new BoosterStore.ProtocolEntry(1.25D, 1_000L),
                new BoosterStore.ProtocolEntry(1.5D, 2_000L)
        ));

        clocks.monotonicNanos += TimeUnit.SECONDS.toNanos(1L);
        BoosterStore.Snapshot afterFirstExpiry = store.getSnapshot(BoosterStore.Kind.SHARD);
        assertEquals(1, afterFirstExpiry.entries().size());
        assertEquals(1.5D, afterFirstExpiry.entries().getFirst().multiplier());

        clocks.monotonicNanos += TimeUnit.SECONDS.toNanos(1L);
        assertFalse(store.getSnapshot(BoosterStore.Kind.SHARD).hasAny());
    }

    @Test
    void expiryPredicateDoesNotRoundSubMillisecondRemainderDown() {
        MutableClocks clocks = new MutableClocks(0L, 0L);
        BoosterStore store = new BoosterStore(clocks::wallMillis, clocks::monotonicNanos);
        store.replace(BoosterStore.Kind.MONEY, List.of(new BoosterStore.ProtocolEntry(1.5D, 1L)));
        BoosterStore.Entry entry = store.getSnapshot(BoosterStore.Kind.MONEY).entries().getFirst();

        clocks.monotonicNanos = TimeUnit.MILLISECONDS.toNanos(1L) - 1L;
        assertEquals(0L, entry.remainingMillis());
        assertFalse(entry.expired());

        clocks.monotonicNanos++;
        assertTrue(entry.expired());
    }

    private static final class MutableClocks {
        private long wallMillis;
        private long monotonicNanos;

        private MutableClocks(long wallMillis, long monotonicNanos) {
            this.wallMillis = wallMillis;
            this.monotonicNanos = monotonicNanos;
        }

        private long wallMillis() {
            return wallMillis;
        }

        private long monotonicNanos() {
            return monotonicNanos;
        }
    }
}
