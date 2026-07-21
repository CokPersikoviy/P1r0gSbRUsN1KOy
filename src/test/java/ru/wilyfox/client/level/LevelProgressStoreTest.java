package ru.wilyfox.client.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LevelProgressStoreTest {
    @Test
    void partialStatisticUpdatePreservesMissingValues() {
        LevelProgressStore store = new LevelProgressStore();
        store.updateCurrent(12, 1_000, 2_500.0D);

        store.updateCurrent(null, 1_250, null);

        LevelProgressStore.LevelProgressSnapshot snapshot = store.getSnapshot();
        assertEquals(12, snapshot.level());
        assertEquals(1_250, snapshot.blocks());
        assertEquals(2_500.0D, snapshot.money());
    }
}
