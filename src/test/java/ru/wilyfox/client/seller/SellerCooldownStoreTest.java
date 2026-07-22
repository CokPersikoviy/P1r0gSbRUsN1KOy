package ru.wilyfox.client.seller;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.protocol.DwSellerEntry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellerCooldownStoreTest {
    @Test
    void usesEvoPlusWholeSecondReadyBoundary() {
        AtomicLong now = new AtomicLong(TimeUnit.SECONDS.toNanos(5L));
        SellerCooldownStore store = new SellerCooldownStore(now::get);
        store.replace(List.of(
                new DwSellerEntry("negative", "Negative", -1L),
                new DwSellerEntry("zero", "Zero", 0L),
                new DwSellerEntry("sub_second", "Sub-second", 999L),
                new DwSellerEntry("one_second", "One second", 1_000L)
        ));

        Map<String, SellerCooldownStore.Entry> entries = byId(store);
        assertTrue(entries.get("negative").ready());
        assertTrue(entries.get("zero").ready());
        assertTrue(entries.get("sub_second").ready());
        assertFalse(entries.get("one_second").ready());
        assertEquals(1_000L, entries.get("one_second").remainingMillis());
    }

    @Test
    void countdownUsesMonotonicTimeAndExpiresAtDeadline() {
        AtomicLong now = new AtomicLong(TimeUnit.SECONDS.toNanos(10L));
        SellerCooldownStore store = new SellerCooldownStore(now::get);
        store.replace(List.of(new DwSellerEntry("lucas", "Lucas", 2_999L)));
        SellerCooldownStore.Entry entry = store.getEntries().getFirst();

        assertEquals(2_000L, entry.remainingMillis());

        now.addAndGet(TimeUnit.SECONDS.toNanos(2L) - 1L);
        assertEquals(0L, entry.remainingMillis());
        assertFalse(entry.ready());

        now.incrementAndGet();
        assertTrue(entry.ready());
    }

    @Test
    void replacementIsACompleteSnapshotAndDuplicateIdsUseLastValue() {
        AtomicLong now = new AtomicLong();
        SellerCooldownStore store = new SellerCooldownStore(now::get);
        store.replace(List.of(
                new DwSellerEntry("lucas", "Old Lucas", 5_000L),
                new DwSellerEntry("liam", "Liam", 6_000L),
                new DwSellerEntry("lucas", "New Lucas", 7_000L)
        ));

        assertEquals(2, store.getEntries().size());
        assertEquals("New Lucas", byId(store).get("lucas").name());

        store.replace(List.of(new DwSellerEntry("liam", "Liam", -1L)));

        assertEquals(1, store.getEntries().size());
        assertEquals("liam", store.getEntries().getFirst().id());
        assertTrue(store.getEntries().getFirst().ready());
    }

    private static Map<String, SellerCooldownStore.Entry> byId(SellerCooldownStore store) {
        return store.getEntries().stream()
                .collect(Collectors.toMap(SellerCooldownStore.Entry::id, Function.identity()));
    }
}
