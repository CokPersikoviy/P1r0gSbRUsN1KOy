package ru.wilyfox.client.protocol;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityTimerHistoryTest {
    @Test
    void incrementalUpdatesDoNotRetriggerAnotherActiveAbility() {
        ProtocolState state = new ProtocolState();
        assertTrue(update(state, Map.of("WIND", 10_000L), 1_000L));
        assertTrue(update(state, Map.of("FIRE", 5_000L), 2_000L));

        // WIND was absent from the FIRE packet but is still counting down.
        assertFalse(update(state, Map.of("WIND", 8_000L), 3_000L));
        assertFalse(update(state, Map.of("FIRE", 3_000L), 4_000L));
        assertEquals(2, state.abilityTimerHistory.size());
    }

    @Test
    void aNewCycleStillTriggersAfterExpiryOrExplicitReadyUpdate() {
        ProtocolState state = new ProtocolState();
        assertTrue(update(state, Map.of("WIND", 2_000L), 1_000L));
        assertTrue(update(state, Map.of("WIND", 10_000L), 3_000L));
        assertFalse(update(state, Map.of("WIND", 0L), 4_000L));
        assertTrue(update(state, Map.of("WIND", 10_000L), 4_001L));
    }

    @Test
    void thresholdAndExemptAbilitiesKeepTheirExistingBehavior() {
        ProtocolState state = new ProtocolState();
        assertFalse(update(state, Map.of("SERAPHIM", 10_000L), 1_000L));
        assertTrue(update(state, Map.of("WIND", 10_000L), 1_000L));
        assertFalse(update(state, Map.of("WIND", 10_500L), 2_000L));
        assertTrue(update(state, Map.of("WIND", 11_001L), 3_000L));
    }

    @Test
    void expiredSamplesAndDisconnectedSessionsDoNotRetainHistory() {
        ProtocolState state = new ProtocolState();
        update(state, Map.of("WIND", 1_000L), 1_000L);
        update(state, Map.of("FIRE", 3_000L), 3_000L);
        assertEquals(1, state.abilityTimerHistory.size());
        state.resetRuntimeState();
        assertTrue(state.abilityTimerHistory.isEmpty());
    }

    private static boolean update(ProtocolState state, Map<String, Long> timers, long now) {
        boolean triggered = ProtocolPayloadSupport.shouldTriggerRuneSetCooldown(state, timers, now);
        ProtocolPayloadSupport.rememberAbilityTimers(state, timers, now);
        return triggered;
    }
}
