package ru.wilyfox.client.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void zeroRequirementsReplacePreviousLevelInfo() {
        LevelProgressStore store = new LevelProgressStore();
        store.updateRequirements(10_000, 25_000.0D, false);

        store.updateRequirements(0, 0.0D, false);

        LevelProgressStore.LevelProgressSnapshot snapshot = store.getSnapshot();
        assertEquals(0, snapshot.requiredBlocks());
        assertEquals(0.0D, snapshot.requiredMoney());
    }

    @Test
    void progressAveragesBlocksAndMoneyAndMaxLevelIsNeverCompleted() {
        LevelProgressStore store = new LevelProgressStore();
        store.updateFromLevelInfo(12, 50, 25.0D, 100, 100.0D, false);

        assertEquals(0.375D, store.getSnapshot().progress());
        assertFalse(store.getSnapshot().completed());

        store.updateFromLevelInfo(13, 500, 1_000.0D, 0, 0.0D, true);

        assertTrue(store.getSnapshot().available());
        assertTrue(store.getSnapshot().maxLevel());
        assertFalse(store.getSnapshot().completed());
        assertEquals(1.0D, store.getSnapshot().progress());
    }

    @Test
    void completionRevisionChangesOnlyOnFullLevelInfoTransition() {
        LevelProgressStore store = new LevelProgressStore();
        store.updateFromLevelInfo(10, 50, 50.0D, 100, 100.0D, false);
        assertEquals(0L, store.getSnapshot().lastCompletionRevision());

        store.updateCurrent(null, 100, 100.0D);
        assertTrue(store.getSnapshot().completed());
        assertEquals(0L, store.getSnapshot().lastCompletionRevision());

        store.updateFromLevelInfo(10, 100, 100.0D, 100, 100.0D, false);
        assertEquals(2L, store.getSnapshot().lastCompletionRevision());

        store.updateFromLevelInfo(10, 100, 100.0D, 100, 100.0D, false);
        assertEquals(2L, store.getSnapshot().lastCompletionRevision());

        store.updateFromLevelInfo(11, 0, 0.0D, 200, 200.0D, false);
        store.updateFromLevelInfo(11, 200, 200.0D, 200, 200.0D, false);
        assertEquals(5L, store.getSnapshot().lastCompletionRevision());
    }
}
