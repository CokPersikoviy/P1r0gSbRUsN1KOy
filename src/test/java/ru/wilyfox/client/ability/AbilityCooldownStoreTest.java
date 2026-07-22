package ru.wilyfox.client.ability;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityCooldownStoreTest {
    @Test
    void acceptsOnlyProtocolTimersAboveOneSecondAndIgnoresInactiveUpdates() {
        AtomicLong now = new AtomicLong(10_000L);
        AbilityCooldownStore store = new AbilityCooldownStore(now::get);

        store.replaceCooldowns(Map.of("ACTIVE", 10_000L, "ONE_SECOND", 1_000L, "READY", 0L));
        assertEquals(1, store.getActiveEntries().size());
        assertEquals(20_000L, store.getActiveEntries().getFirst().endsAt());

        now.set(11_000L);
        store.replaceCooldowns(Map.of("OTHER", 5_000L));
        assertEquals(2, store.getActiveEntries().size());

        now.set(12_000L);
        store.replaceCooldowns(Map.of("ACTIVE", 0L));

        assertEquals(20_000L, store.getActiveEntries().getFirst().endsAt());
        assertEquals(8_000L, store.getActiveEntries().getFirst().remainingMillis());
    }

    @Test
    void refreshesProtocolDeadlineFromExactRemainingTime() {
        AtomicLong now = new AtomicLong(10_000L);
        AbilityCooldownStore store = new AbilityCooldownStore(now::get);
        store.replaceCooldowns(Map.of("ABILITY", 10_000L));

        now.set(12_000L);
        store.replaceCooldowns(Map.of("ABILITY", 7_000L));

        assertEquals(19_000L, store.getActiveEntries().getFirst().endsAt());
        assertEquals(7_000L, store.getActiveEntries().getFirst().remainingMillis());
    }

    @Test
    void resolvesAbilityNameWhenTypesArriveAfterTimer() {
        AtomicLong now = new AtomicLong(10_000L);
        AbilityCooldownStore store = new AbilityCooldownStore(now::get);
        store.replaceCooldowns(Map.of("WIND", 10_000L));

        assertEquals("WIND", store.getActiveEntries().getFirst().name());

        store.replaceTypes(Map.of("WIND", "\u041f\u043e\u0440\u044b\u0432 \u0432\u0435\u0442\u0440\u0430"));
        assertEquals("\u041f\u043e\u0440\u044b\u0432 \u0432\u0435\u0442\u0440\u0430", store.getActiveEntries().getFirst().name());
    }

    @Test
    void preservesExternalCooldownBehaviorForGourmet() {
        AtomicLong now = new AtomicLong(10_000L);
        AbilityCooldownStore store = new AbilityCooldownStore(now::get);
        store.replaceExternalCooldown("gourmetcd", "\u0413\u0443\u0440\u043c\u0430\u043d", 5_000L);

        assertEquals("\u0413\u0443\u0440\u043c\u0430\u043d", store.getActiveEntries().getFirst().name());

        store.replaceExternalCooldown("gourmetcd", "\u0413\u0443\u0440\u043c\u0430\u043d", 0L);
        assertTrue(store.getActiveEntries().isEmpty());
    }
}
