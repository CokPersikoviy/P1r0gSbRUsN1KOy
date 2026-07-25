package ru.wilyfox.client.protocol;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.statistic.DailyBlocksStore;

import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyBlocksProtocolTest {
    @Test
    void readsBlocksMinedIn24HoursFromStatisticMap() {
        OptionalInt result = ProtocolPayloadSupport.extractBlocksMinedIn24Hours(
                new DwStatisticInfoPacket(Map.of(
                        "statistic",
                        "{\"MONEY_FROM_SHAFT\":12.5,\"BLOCKS_MINED_IN_24H\":345.9}"
                ))
        );

        assertTrue(result.isPresent());
        assertEquals(345, result.getAsInt());
    }

    @Test
    void acceptsLegacyOrdinalStatisticArray() {
        String statistic = "[" + "0,".repeat(24) + "123]";

        OptionalInt result = ProtocolPayloadSupport.extractBlocksMinedIn24Hours(
                new DwStatisticInfoPacket(Map.of("statistic", statistic))
        );

        assertTrue(result.isPresent());
        assertEquals(123, result.getAsInt());
    }

    @Test
    void missingStatisticFieldDoesNotInventDailyBlocks() {
        assertFalse(ProtocolPayloadSupport.extractBlocksMinedIn24Hours(
                new DwStatisticInfoPacket(Map.of("blocks", "1000"))
        ).isPresent());
    }

    @Test
    void storeMatchesEvoPlusWindowCalibration() {
        DailyBlocksStore store = new DailyBlocksStore();

        store.update(10_000, OptionalInt.of(250));
        assertEquals(new DailyBlocksStore.Snapshot(250, true), store.getSnapshot());

        store.update(10_025, OptionalInt.empty());
        assertEquals(275, store.getSnapshot().blocks());

        store.update(10_030, OptionalInt.of(240));
        assertEquals(240, store.getSnapshot().blocks());

        store.update(10_040, OptionalInt.of(240));
        assertEquals(250, store.getSnapshot().blocks());

        store.clear();
        assertEquals(new DailyBlocksStore.Snapshot(0, false), store.getSnapshot());
    }
}
