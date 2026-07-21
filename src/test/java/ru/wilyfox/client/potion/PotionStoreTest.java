package ru.wilyfox.client.potion;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionStoreTest {
    @Test
    void appliesOnlyPositivePotionCooldowns() {
        PotionStore store = new PotionStore();
        store.applyCooldownUpdate(Map.of(15, 60_000L, 41, -1L));

        Map<Integer, Long> remaining = store.getCooldownsRemaining();
        assertTrue(remaining.containsKey(15));
        assertTrue(remaining.get(15) > 0L);
        assertFalse(remaining.containsKey(41));
    }
}
