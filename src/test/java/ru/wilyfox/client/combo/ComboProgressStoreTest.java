package ru.wilyfox.client.combo;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboProgressStoreTest {
    @Test
    void fullUpdateMatchesEvoPlusFieldRules() {
        ComboProgressStore store = new ComboProgressStore();

        store.updateCombo(0.5D, 0.75D, -25, 1_000);

        ComboProgressStore.Snapshot snapshot = store.getSnapshot();
        assertTrue(snapshot.available());
        assertEquals(1.0D, snapshot.booster());
        assertEquals(0.75D, snapshot.nextBooster());
        assertEquals(-25, snapshot.blocks());
        assertEquals(1_000, snapshot.requiredBlocks());
        assertEquals(0.0D, snapshot.progress());
        assertFalse(snapshot.maxed());
    }

    @Test
    void comboBlocksPreservesMetadataAndCancelsExpiry() {
        AtomicLong now = new AtomicLong(10_000L);
        ComboProgressStore store = new ComboProgressStore(now::get);
        store.updateCombo(2.0D, 2.5D, 100, 500);
        store.setRemainingSeconds(30L);

        store.updateBlocks(125);

        ComboProgressStore.Snapshot snapshot = store.getSnapshot();
        assertEquals(2.0D, snapshot.booster());
        assertEquals(2.5D, snapshot.nextBooster());
        assertEquals(125, snapshot.blocks());
        assertEquals(500, snapshot.requiredBlocks());
        assertEquals(0L, snapshot.expiryTimestamp());
    }

    @Test
    void warningStoresAbsoluteExpiryAndCountsDown() {
        AtomicLong now = new AtomicLong(10_000L);
        ComboProgressStore store = new ComboProgressStore(now::get);
        store.updateCombo(2.0D, 2.5D, 100, 500);

        store.setRemainingSeconds(30L);

        ComboProgressStore.Snapshot snapshot = store.getSnapshot();
        assertEquals(40_000L, snapshot.expiryTimestamp());
        assertEquals(30L, snapshot.remainingSeconds(10_000L));
        assertEquals(1L, snapshot.remainingSeconds(39_000L));
        assertEquals(0L, snapshot.remainingSeconds(40_000L));
        assertFalse(snapshot.expiring(40_000L));
    }

    @Test
    void maxedUsesExactMultiplierEquality() {
        ComboProgressStore store = new ComboProgressStore();
        store.updateCombo(2.0D, 2.00001D, 500, 500);
        assertFalse(store.getSnapshot().maxed());
        assertTrue(store.getSnapshot().completed());

        store.updateCombo(2.0D, 2.0D, 500, 500);
        assertTrue(store.getSnapshot().maxed());
    }
}
