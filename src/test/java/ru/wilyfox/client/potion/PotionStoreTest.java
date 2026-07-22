package ru.wilyfox.client.potion;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.protocol.DwPotionTypeEntry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionStoreTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void appliesOnlyPositivePotionCooldowns() {
        PotionStore store = new PotionStore();
        store.applyCooldownUpdate(Map.of(15, 60_000L, 41, -1L));

        Map<Integer, Long> remaining = store.getCooldownsRemaining();
        assertTrue(remaining.containsKey(15));
        assertTrue(remaining.get(15) > 0L);
        assertFalse(remaining.containsKey(41));
    }

    @Test
    void resolvesAndSortsPotionCooldownEntriesByRemainingTime() {
        PotionStore store = new PotionStore();
        store.applyCooldownUpdate(Map.of(41, 60_000L, 15, 20_000L));

        assertEquals("\u0417\u0435\u043b\u044c\u0435 #41", store.getCooldownEntries(0L).get(1).name());

        store.replaceTypes(List.of(
                new DwPotionTypeEntry(15, 150, "\u00a7a\u0417\u0435\u043b\u044c\u0435 \u0434\u043e\u0431\u044b\u0447\u0438"),
                new DwPotionTypeEntry(41, 410, "\u0417\u0435\u043b\u044c\u0435 \u0441\u0438\u043b\u044b")
        ));

        List<PotionStore.CooldownPotionEntry> entries = store.getCooldownEntries(0L);

        assertEquals(List.of(15, 41), entries.stream().map(PotionStore.CooldownPotionEntry::id).toList());
        assertEquals("\u0417\u0435\u043b\u044c\u0435 \u0434\u043e\u0431\u044b\u0447\u0438", entries.getFirst().name());
    }

    @Test
    void keepsReadyPotionCooldownDuringDisplayGrace() throws InterruptedException {
        PotionStore store = new PotionStore();
        store.applyCooldownUpdate(Map.of(15, 1L));
        Thread.sleep(20L);

        List<PotionStore.CooldownPotionEntry> graceEntries = store.getCooldownEntries(3_000L);
        assertEquals(1, graceEntries.size());
        assertTrue(graceEntries.getFirst().remainingMillis() <= 0L);

        assertTrue(store.getCooldownEntries(0L).isEmpty());
    }
}
