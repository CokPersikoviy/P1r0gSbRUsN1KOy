package ru.wilyfox.client.effect;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveEffectStoreTest {
    @Test
    void repeatedMessageRestartsEffectDuration() {
        AtomicLong now = new AtomicLong(1_000L);
        ActiveEffectStore store = new ActiveEffectStore(now::get);

        store.activate("regeneration_disabled", "Regeneration Disabled", ActiveEffectKind.DEBUFF, 7_000L);
        now.addAndGet(3_000L);
        store.activate("regeneration_disabled", "Regeneration Disabled", ActiveEffectKind.DEBUFF, 7_000L);

        ActiveEffectStore.Entry entry = store.getActiveEntries().getFirst();
        assertEquals(7_000L, entry.remainingMillis());
        assertEquals(1, store.getActiveEntries().size());
    }

    @Test
    void expiredEffectsAreRemoved() {
        AtomicLong now = new AtomicLong();
        ActiveEffectStore store = new ActiveEffectStore(now::get);
        store.activate("magic_power_reduced", "Magic Power Reduced", ActiveEffectKind.DEBUFF, 10_000L);

        assertTrue(store.hasActiveEntries());
        now.set(10_000L);
        assertFalse(store.hasActiveEntries());
    }
}
