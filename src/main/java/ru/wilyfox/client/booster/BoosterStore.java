package ru.wilyfox.client.booster;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class BoosterStore {
    public enum Kind {
        SHARD,
        MONEY,
        SHAFT
    }

    private final Map<Kind, List<Entry>> entries = new EnumMap<>(Kind.class);

    public BoosterStore() {
        for (Kind kind : Kind.values()) {
            entries.put(kind, new ArrayList<>());
        }
    }

    public void replace(Kind kind, List<ProtocolEntry> protocolEntries) {
        if (kind == null) {
            return;
        }

        long now = System.currentTimeMillis();
        List<Entry> mapped = new ArrayList<>();
        if (protocolEntries != null) {
            for (ProtocolEntry entry : protocolEntries) {
                if (entry == null || entry.multiplier() <= 0.0D || entry.remainingMillis() <= 0L) {
                    continue;
                }

                mapped.add(new Entry(entry.multiplier(), now, now + entry.remainingMillis(), entry.remainingMillis()));
            }
        }
        mapped.sort(java.util.Comparator.comparingLong(Entry::endsAt));

        entries.put(kind, mapped);
    }

    public Snapshot getSnapshot(Kind kind) {
        cleanup(System.currentTimeMillis());
        return new Snapshot(List.copyOf(entries.get(kind)));
    }

    public boolean hasAnyActive() {
        cleanup(System.currentTimeMillis());
        return entries.values().stream().anyMatch(list -> !list.isEmpty());
    }

    public void clear() {
        entries.values().forEach(List::clear);
    }

    private void cleanup(long now) {
        for (List<Entry> values : entries.values()) {
            values.removeIf(entry -> entry.endsAt() <= now);
        }
    }

    public record Entry(
            double multiplier,
            long startedAt,
            long endsAt,
            long durationMillis
    ) {
        public long remainingMillis() {
            return Math.max(0L, endsAt - System.currentTimeMillis());
        }

        public float progress() {
            if (durationMillis <= 0L) {
                return 0.0F;
            }

            return remainingMillis() / (float) durationMillis;
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
