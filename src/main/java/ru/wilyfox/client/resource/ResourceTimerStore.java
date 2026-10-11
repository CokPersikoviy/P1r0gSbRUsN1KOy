package ru.wilyfox.client.resource;

import com.google.gson.Gson;
import ru.wilyfox.utils.AtomicFileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Absolute deadlines survive both reconnects and JVM restarts. Owned by the client thread. */
public final class ResourceTimerStore {
    private static final Gson GSON = new Gson();
    private static final int LIMIT = 100;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    public record Entry(String owner, long endsAt, long lastNoticeAt) {
        Entry noticed(long now) { return new Entry(owner, endsAt, now); }
    }
    public boolean observe(String owner, ResourceCountdown countdown, long now) {
        String key = owner.toLowerCase(java.util.Locale.ROOT);
        long endsAt = now + countdown.millis();
        Entry old = entries.get(key);
        // Reloading the ready hologram must not reset the persisted reminder interval.
        if (old != null && countdown.millis() == 0 && old.endsAt() <= now) return false;
        // A minute-only label jitters by up to a minute. Do not rewrite the disk every observation.
        boolean newCycle = old != null && old.endsAt() <= now && countdown.millis() > 0;
        if (old != null && !newCycle && Math.abs(old.endsAt() - endsAt) <= countdown.precisionMillis() + 2000L) return false;
        if (old == null && entries.size() >= LIMIT) entries.remove(entries.keySet().iterator().next());
        entries.put(key, new Entry(key, endsAt, 0));
        return true;
    }
    public Entry entry(String owner) {
        return entries.get(owner.toLowerCase(java.util.Locale.ROOT));
    }
    public List<Entry> reminders(String owner, long now, int intervalMinutes) {
        var due = new ArrayList<Entry>();
        Entry entry = entry(owner);
        if (entry != null) {
            if (entry.endsAt() <= now && (entry.lastNoticeAt() == 0 || now - entry.lastNoticeAt() >= Math.max(1, intervalMinutes) * 60_000L)) {
                Entry updated = entry.noticed(now); entries.put(entry.owner(), updated); due.add(updated);
            }
        }
        return due;
    }
    public List<Entry> snapshot() { return List.copyOf(entries.values()); }
    public void load(Path file) throws IOException {
        if (!Files.exists(file)) return;
        if (Files.size(file) > 512_000) throw new IOException("Resource timer file too large");
        try (var reader = Files.newBufferedReader(file)) {
            Entry[] saved = GSON.fromJson(reader, Entry[].class);
            if (saved == null) return;
            entries.clear();
            for (Entry e : saved) {
                if (e != null && e.owner() != null && e.owner().matches("[a-zA-Z0-9_]{1,32}")
                        && e.endsAt() > 0 && e.lastNoticeAt() >= 0 && entries.size() < LIMIT) {
                    String owner = e.owner().toLowerCase(java.util.Locale.ROOT);
                    entries.put(owner, new Entry(owner, e.endsAt(), e.lastNoticeAt()));
                }
            }
        }
    }
    public static void save(Path file, List<Entry> snapshot) throws IOException {
        AtomicFileWriter.write(file, writer -> GSON.toJson(snapshot, writer));
    }
}
