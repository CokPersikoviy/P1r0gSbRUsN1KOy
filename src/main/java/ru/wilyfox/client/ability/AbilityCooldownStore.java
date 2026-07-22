package ru.wilyfox.client.ability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

public class AbilityCooldownStore {
    private static final long PROTOCOL_DISPLAY_THRESHOLD_MS = 1_000L;

    private final Map<String, StoredCooldown> entries = new LinkedHashMap<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    private final LongSupplier clock;

    public AbilityCooldownStore() {
        this(System::currentTimeMillis);
    }

    AbilityCooldownStore(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void replaceTypes(Map<String, String> updatedNames) {
        names.clear();
        names.putAll(updatedNames);
    }

    public void replaceCooldowns(Map<String, Long> remainingMillisById) {
        cleanup();

        for (Map.Entry<String, Long> entry : remainingMillisById.entrySet()) {
            String id = entry.getKey();
            long remainingMillis = entry.getValue();
            if (id != null && !id.isBlank() && remainingMillis > PROTOCOL_DISPLAY_THRESHOLD_MS) {
                entries.put(id, new StoredCooldown(clock.getAsLong() + remainingMillis, null));
            }
        }
    }

    public void replaceExternalCooldown(String id, String name, long remainingMillis) {
        if (id == null || id.isBlank()) {
            return;
        }

        if (remainingMillis <= 0L) {
            entries.remove(id);
            return;
        }

        String explicitName = name != null && !name.isBlank() ? name : id;
        entries.put(id, new StoredCooldown(clock.getAsLong() + remainingMillis, explicitName));
    }

    public List<Entry> getActiveEntries() {
        cleanup();
        long now = clock.getAsLong();
        List<Entry> result = new ArrayList<>(entries.size());
        entries.forEach((id, cooldown) -> result.add(new Entry(
                id,
                resolveName(id, cooldown),
                cooldown.endsAt(),
                Math.max(0L, cooldown.endsAt() - now)
        )));
        return result;
    }

    public boolean hasActiveEntries() {
        cleanup();
        return !entries.isEmpty();
    }

    public void clear() {
        entries.clear();
        names.clear();
    }

    private void cleanup() {
        long now = clock.getAsLong();
        entries.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now);
    }

    private String resolveName(String id, StoredCooldown cooldown) {
        if (cooldown.explicitName() != null && !cooldown.explicitName().isBlank()) {
            return cooldown.explicitName();
        }
        String resolved = names.get(id);
        return resolved != null && !resolved.isBlank() ? resolved : id;
    }

    private record StoredCooldown(long endsAt, String explicitName) {
    }

    public record Entry(
            String id,
            String name,
            long endsAt,
            long remainingMillis
    ) {
    }
}
