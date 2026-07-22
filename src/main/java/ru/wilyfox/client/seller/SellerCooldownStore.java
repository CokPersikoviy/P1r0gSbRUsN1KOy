package ru.wilyfox.client.seller;

import ru.wilyfox.client.protocol.DwSellerEntry;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class SellerCooldownStore {
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final LongSupplier monotonicNanos;

    public SellerCooldownStore() {
        this(System::nanoTime);
    }

    SellerCooldownStore(LongSupplier monotonicNanos) {
        this.monotonicNanos = monotonicNanos;
    }

    public void replace(List<DwSellerEntry> sellers) {
        long now = monotonicNanos.getAsLong();
        entries.clear();

        for (DwSellerEntry seller : sellers) {
            long remainingSeconds = seller.remainingMillis() < 0L
                    ? 0L
                    : seller.remainingMillis() / 1000L;
            long durationNanos = saturatingMultiply(remainingSeconds, TimeUnit.SECONDS.toNanos(1L));
            entries.put(seller.id(), new Entry(
                    seller.id(),
                    sanitizeName(seller.name(), seller.id()),
                    now,
                    durationNanos,
                    monotonicNanos
            ));
        }
    }

    public List<Entry> getEntries() {
        List<Entry> result = new ArrayList<>(entries.values());
        result.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public boolean hasEntries() {
        return !entries.isEmpty();
    }

    public void clear() {
        entries.clear();
    }

    private static String sanitizeName(String name, String fallbackId) {
        String stripped = Formatting.stripMinecraftFormatting(name == null ? "" : name)
                .replaceAll("(?i)&[0-9A-FK-ORX]", "")
                .trim();
        return stripped.isBlank() ? fallbackId : stripped;
    }

    private static long saturatingMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return (left < 0L) == (right < 0L) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    public static final class Entry {
        private final String id;
        private final String name;
        private final long receivedAtNanos;
        private final long durationNanos;
        private final LongSupplier monotonicNanos;

        private Entry(
                String id,
                String name,
                long receivedAtNanos,
                long durationNanos,
                LongSupplier monotonicNanos
        ) {
            this.id = id;
            this.name = name;
            this.receivedAtNanos = receivedAtNanos;
            this.durationNanos = durationNanos;
            this.monotonicNanos = monotonicNanos;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public boolean ready() {
            return readyAt(monotonicNanos.getAsLong());
        }

        boolean readyAt(long monotonicNowNanos) {
            return elapsedNanos(monotonicNowNanos) >= durationNanos;
        }

        public long remainingMillis() {
            return remainingMillisAt(monotonicNanos.getAsLong());
        }

        long remainingMillisAt(long monotonicNowNanos) {
            long remainingNanos = durationNanos - elapsedNanos(monotonicNowNanos);
            return remainingNanos <= 0L ? 0L : TimeUnit.NANOSECONDS.toMillis(remainingNanos);
        }

        private long elapsedNanos(long monotonicNowNanos) {
            long elapsed = monotonicNowNanos - receivedAtNanos;
            return elapsed < 0L ? 0L : elapsed;
        }
    }
}
