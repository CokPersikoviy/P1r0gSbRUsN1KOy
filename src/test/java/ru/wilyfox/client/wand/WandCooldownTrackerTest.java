package ru.wilyfox.client.wand;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandCooldownTrackerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void keepsWindAndAwakenedWindAsSeparateProtocolTypes() {
        AtomicLong now = new AtomicLong(10_000L);
        WandCooldownTracker tracker = new WandCooldownTracker(now::get);
        tracker.replaceTypes(
                Map.of(501, "Посох ветра", 502, "Посох вихря"),
                Map.of(501, 7_501, 502, 7_502)
        );
        tracker.replaceProtocol(Map.of(501, 30_000L, 502, 45_000L));

        var entries = tracker.getActiveEntries();
        assertEquals(2, entries.size());
        assertEquals(Set.of("wind|wind", "wind|awakened-wind"), entries.stream()
                .map(WandCooldownTracker.WandCooldownEntry::key)
                .collect(Collectors.toSet()));
        assertEquals(Set.of(7_501.0F, 7_502.0F), entries.stream()
                .map(entry -> entry.stack().get(DataComponents.CUSTOM_MODEL_DATA).getFloat(0))
                .collect(Collectors.toSet()));
    }

    @Test
    void resolvesMetadataWhenStaffTypesArriveAfterTimer() {
        AtomicLong now = new AtomicLong(20_000L);
        WandCooldownTracker tracker = new WandCooldownTracker(now::get);
        tracker.replaceProtocol(Map.of(700, 30_000L));

        assertTrue(tracker.getActiveEntries().isEmpty());

        tracker.replaceTypes(Map.of(700, "Посох силы"), Map.of(700, 8_700));

        assertEquals("Посох силы", tracker.getActiveEntries().getFirst().name());
        assertEquals(8_700.0F, tracker.getActiveEntries().getFirst().stack()
                .get(DataComponents.CUSTOM_MODEL_DATA).getFloat(0));
    }

    @Test
    void acceptsOnlyProtocolTimersAboveTwoSeconds() {
        AtomicLong now = new AtomicLong(30_000L);
        WandCooldownTracker tracker = new WandCooldownTracker(now::get);
        tracker.replaceTypes(
                Map.of(1, "Посох силы", 2, "Посох огня"),
                Map.of(1, 1, 2, 2)
        );

        tracker.replaceProtocol(Map.of(1, 2_000L, 2, 2_001L));

        assertEquals(1, tracker.getActiveEntries().size());
        assertEquals("Посох огня", tracker.getActiveEntries().getFirst().name());
    }

    @Test
    void treatsStaffTimersAsIncrementalUpdates() {
        AtomicLong now = new AtomicLong(35_000L);
        WandCooldownTracker tracker = new WandCooldownTracker(now::get);
        tracker.replaceTypes(
                Map.of(1, "Посох силы", 2, "Посох огня"),
                Map.of(1, 1, 2, 2)
        );
        tracker.replaceProtocol(Map.of(1, 10_000L, 2, 20_000L));

        now.addAndGet(1_000L);
        tracker.replaceProtocol(Map.of(1, 8_000L));

        assertEquals(Set.of("Посох силы", "Посох огня"), tracker.getActiveEntries().stream()
                .map(WandCooldownTracker.WandCooldownEntry::name)
                .collect(Collectors.toSet()));
    }

    @Test
    void hidesStaffInEvoPlusReadyWindow() {
        AtomicLong now = new AtomicLong(40_000L);
        WandCooldownTracker tracker = new WandCooldownTracker(now::get);
        tracker.replaceTypes(Map.of(1, "Посох силы"), Map.of(1, 1));
        tracker.replaceProtocol(Map.of(1, 5_000L));

        now.addAndGet(3_999L);
        assertTrue(tracker.hasActiveEntries());

        now.incrementAndGet();
        assertFalse(tracker.hasActiveEntries());
    }

    @Test
    void numericModeKeepsStaffVisibleThroughFinalSecond() {
        AtomicLong now = new AtomicLong(45_000L);
        WandCooldownTracker tracker = new WandCooldownTracker(now::get);
        tracker.replaceTypes(Map.of(1, "Посох силы"), Map.of(1, 1));
        tracker.replaceProtocol(Map.of(1, 5_000L));

        now.addAndGet(4_000L);
        assertFalse(tracker.hasActiveEntries());
        assertTrue(tracker.hasActiveEntries(true));
        assertEquals(1, tracker.getActiveEntries(true).size());

        now.addAndGet(1_000L);
        assertFalse(tracker.hasActiveEntries(true));
    }

    @Test
    void serverRefreshReplacesDeadlineExactly() {
        AtomicLong now = new AtomicLong(50_000L);
        WandCooldownTracker tracker = new WandCooldownTracker(now::get);
        tracker.replaceTypes(Map.of(1, "Посох силы"), Map.of(1, 1));
        tracker.replaceProtocol(Map.of(1, 5_000L));

        now.addAndGet(1_000L);
        tracker.replaceProtocol(Map.of(1, 3_000L));

        assertEquals(54_000L, tracker.getActiveEntries().getFirst().endsAt());
    }

    @Test
    void recognizesWindVariantsAndHarpyAliasForLocalTracking() {
        assertTrue(WandCooldownTracker.isWindStaffName("Посох ветра"));
        assertTrue(WandCooldownTracker.isWindStaffName("Величие гарпии"));
        assertTrue(WandCooldownTracker.isWindStaffName("Посох вихря"));
        assertTrue(WandCooldownTracker.isWindStaffName("Посох вихря Пробужденный"));
        assertFalse(WandCooldownTracker.isWindStaffName("Посох силы"));
    }
}
