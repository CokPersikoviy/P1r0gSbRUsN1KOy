package ru.wilyfox.client.effect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class ActiveEffectStore {
    private final Map<String, StoredEffect> effects = new LinkedHashMap<>();
    private final LongSupplier clock;

    public ActiveEffectStore() {
        this(System::currentTimeMillis);
    }

    ActiveEffectStore(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void activate(String id, String displayName, ActiveEffectKind kind, long durationMillis) {
        if (id == null || id.isBlank() || durationMillis <= 0L) {
            return;
        }

        String resolvedName = displayName == null || displayName.isBlank() ? id : displayName;
        ActiveEffectKind resolvedKind = kind == null ? ActiveEffectKind.BUFF : kind;
        effects.put(id, new StoredEffect(
                resolvedName,
                resolvedKind,
                clock.getAsLong() + durationMillis
        ));
    }

    public List<Entry> getActiveEntries() {
        cleanup();
        long now = clock.getAsLong();
        List<Entry> result = new ArrayList<>(effects.size());
        effects.forEach((id, effect) -> result.add(new Entry(
                id,
                effect.displayName(),
                effect.kind(),
                effect.endsAt(),
                Math.max(0L, effect.endsAt() - now)
        )));
        return result;
    }

    public boolean hasActiveEntries() {
        cleanup();
        return !effects.isEmpty();
    }

    public void clear() {
        effects.clear();
    }

    private void cleanup() {
        long now = clock.getAsLong();
        effects.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now);
    }

    private record StoredEffect(
            String displayName,
            ActiveEffectKind kind,
            long endsAt
    ) {
    }

    public record Entry(
            String id,
            String displayName,
            ActiveEffectKind kind,
            long endsAt,
            long remainingMillis
    ) {
    }
}
