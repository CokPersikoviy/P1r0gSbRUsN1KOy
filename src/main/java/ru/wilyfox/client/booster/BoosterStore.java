package ru.wilyfox.client.booster;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class BoosterStore {
    public enum Kind {
        SHARD,
        MONEY,
        SHAFT
    }

    private final Map<Kind, List<Entry>> entries = new EnumMap<>(Kind.class);
    private final LongSupplier wallClockMillis;
    private final LongSupplier monotonicNanos;

    public BoosterStore() {
        this(System::currentTimeMillis, System::nanoTime);
    }

    BoosterStore(LongSupplier wallClockMillis, LongSupplier monotonicNanos) {
        this.wallClockMillis = wallClockMillis;
        this.monotonicNanos = monotonicNanos;
        for (Kind kind : Kind.values()) {
            entries.put(kind, new ArrayList<>());
        }
    }

    public void replace(Kind kind, List<ProtocolEntry> protocolEntries) {
        if (kind == null) {
            return;
        }

        long wallNow = wallClockMillis.getAsLong();
        long monotonicNow = monotonicNanos.getAsLong();
        entries.put(kind, mapEntries(protocolEntries, wallNow, monotonicNow));
    }

    public void replaceAll(Map<Kind, List<ProtocolEntry>> protocolSnapshot) {
        long wallNow = wallClockMillis.getAsLong();
        long monotonicNow = monotonicNanos.getAsLong();

        for (Kind kind : Kind.values()) {
            List<ProtocolEntry> protocolEntries = protocolSnapshot != null ? protocolSnapshot.get(kind) : null;
            entries.put(kind, mapEntries(protocolEntries, wallNow, monotonicNow));
        }
    }

    private List<Entry> mapEntries(List<ProtocolEntry> protocolEntries, long wallNow, long monotonicNow) {
        List<Entry> mapped = new ArrayList<>();
        if (protocolEntries != null) {
            for (ProtocolEntry entry : protocolEntries) {
                if (entry == null || entry.multiplier() <= 0.0D || entry.remainingMillis() <= 0L) {
                    continue;
                }

                mapped.add(new Entry(
                        entry.multiplier(),
                        wallNow,
                        saturatingAdd(wallNow, entry.remainingMillis()),
                        entry.remainingMillis(),
                        saturatingAdd(monotonicNow, TimeUnit.MILLISECONDS.toNanos(entry.remainingMillis())),
                        monotonicNanos
                ));
            }
        }
        mapped.sort(java.util.Comparator.comparingLong(Entry::endsAt));
        return mapped;
    }

    public Snapshot getSnapshot(Kind kind) {
        cleanup(monotonicNanos.getAsLong());
        return new Snapshot(List.copyOf(entries.get(kind)));
    }

    public boolean hasAnyActive() {
        cleanup(monotonicNanos.getAsLong());
        return entries.values().stream().anyMatch(list -> !list.isEmpty());
    }

    public void clear() {
        entries.values().forEach(List::clear);
    }

    private void cleanup(long monotonicNow) {
        for (List<Entry> values : entries.values()) {
            values.removeIf(entry -> entry.hasExpired(monotonicNow));
        }
    }

    private static long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return right >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    public static final class Entry {
        private final double multiplier;
        private final long startedAt;
        private final long endsAt;
        private final long durationMillis;
        private final long monotonicDeadlineNanos;
        private final LongSupplier monotonicNanos;

        private Entry(
                double multiplier,
                long startedAt,
                long endsAt,
                long durationMillis,
                long monotonicDeadlineNanos,
                LongSupplier monotonicNanos
        ) {
            this.multiplier = multiplier;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.durationMillis = durationMillis;
            this.monotonicDeadlineNanos = monotonicDeadlineNanos;
            this.monotonicNanos = monotonicNanos;
        }

        public double multiplier() {
            return multiplier;
        }

        public long startedAt() {
            return startedAt;
        }

        public long endsAt() {
            return endsAt;
        }

        public long durationMillis() {
            return durationMillis;
        }

        public long remainingMillis() {
            long remainingNanos = monotonicDeadlineNanos - monotonicNanos.getAsLong();
            return remainingNanos <= 0L ? 0L : TimeUnit.NANOSECONDS.toMillis(remainingNanos);
        }

        public float progress() {
            if (durationMillis <= 0L) {
                return 0.0F;
            }

            return remainingMillis() / (float) durationMillis;
        }

        public boolean expired() {
            return hasExpired(monotonicNanos.getAsLong());
        }

        private boolean hasExpired(long monotonicNow) {
            return monotonicDeadlineNanos - monotonicNow <= 0L;
        }
    }

    public record Snapshot(List<Entry> entries) {
        public boolean hasAny() {
            return !entries.isEmpty();
        }

        public Entry latest() {
            Entry latest = null;
            for (Entry entry : entries) {
                if (latest == null || entry.endsAt() > latest.endsAt()) {
                    latest = entry;
                }
            }
            return latest;
        }

        public double totalMultiplier() {
            if (entries.isEmpty()) {
                return 1.0D;
            }
            return entries.stream().mapToDouble(Entry::multiplier).sum() - (entries.size() - 1);
        }
    }

    public record ProtocolEntry(double multiplier, long remainingMillis) {
    }
}
