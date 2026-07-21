package ru.wilyfox.client.booster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
