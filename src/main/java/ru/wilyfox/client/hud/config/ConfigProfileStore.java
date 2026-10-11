package ru.wilyfox.client.hud.config;

import com.google.gson.*;
import ru.wilyfox.utils.AtomicFileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** A bounded local library. Main is never replaced by downloads or deleted. */
public final class ConfigProfileStore {
    public static final String MAIN = "main";
    public static final int MAX_DOWNLOADS = 32;
    public record Entry(String id, String name, String author, long downloadedAt) {}
    private final Path directory;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, JsonObject> cache = new HashMap<>();
    private String active = MAIN;

    public ConfigProfileStore(Path directory, JsonObject initial) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
        entries.put(MAIN, new Entry(MAIN, "Main", "", 0));
        var index = directory.resolve("index.json");
        if (Files.exists(index)) {
            try {
                if (Files.size(index) > 32_768) throw new IOException("Invalid profile index");
                var json = JsonParser.parseString(Files.readString(index)).getAsJsonObject();
                if (json.has("profiles")) for (var value : json.getAsJsonArray("profiles")) {
                    var entry = new Gson().fromJson(value, Entry.class);
                    if (entries.size() > MAX_DOWNLOADS || entry == null || !validId(entry.id()) || MAIN.equals(entry.id())
                            || entry.author() == null || !entry.author().matches("[A-Za-z0-9_]{1,16}")) continue;
                    entries.put(entry.id(), new Entry(entry.id(), entry.author(), entry.author(), entry.downloadedAt()));
                }
                if (json.has("active") && entries.containsKey(json.get("active").getAsString())) active = json.get("active").getAsString();
            } catch (RuntimeException | IOException invalid) { active = MAIN; }
        }
        if (!Files.exists(file(MAIN))) write(MAIN, initial);
        try { read(active); } catch (IOException | RuntimeException invalid) { active = MAIN; }
        read(MAIN); // A damaged Main must never be overwritten with a downloaded config.
    }
    public synchronized List<Entry> entries() { return List.copyOf(entries.values()); }
    public synchronized String activeId() { return active; }
    public synchronized JsonObject read(String id) throws IOException {
        if (!entries.containsKey(id)) throw new IOException("Unknown profile");
        var value = cache.get(id);
        if (value == null) {
            if (Files.size(file(id)) > SharedConfigCodec.MAX_BYTES) throw new IOException("Config is too large");
            value = SharedConfigCodec.parse(Files.readString(file(id))); cache.put(id, value);
        }
        return value.deepCopy();
    }
    public synchronized void saveActive(JsonObject value) throws IOException {
        if (!value.equals(cache.get(active))) write(active, value);
    }
    public synchronized void select(String id) throws IOException {
        read(id);
        String previous = active; active = id;
        try { saveIndex(); } catch (IOException failure) { active = previous; throw failure; }
    }
    public synchronized String add(String author, JsonObject value) throws IOException {
        if (author == null || !author.matches("[A-Za-z0-9_]{1,16}")) throw new IOException("Invalid config author");
        if (entries.size() >= MAX_DOWNLOADS + 1) throw new IOException("Profile library is full");
        String id = UUID.randomUUID().toString();
        write(id, SharedConfigCodec.normalize(value));
        entries.put(id, new Entry(id, author, author, System.currentTimeMillis()));
        try { saveIndex(); } catch (IOException failure) { entries.remove(id); cache.remove(id); Files.deleteIfExists(file(id)); throw failure; }
        return id;
    }
    public synchronized void delete(String id) throws IOException {
        if (MAIN.equals(id) || active.equals(id)) throw new IOException("Cannot delete this profile");
        var removed = entries.remove(id); if (removed == null) return;
        try { saveIndex(); } catch (IOException failure) { entries.put(id, removed); throw failure; }
        cache.remove(id); Files.deleteIfExists(file(id));
    }
    private Path file(String id) {
        if (!validId(id)) throw new IllegalArgumentException("Invalid profile ID");
        return directory.resolve(id + ".json");
    }
    private static boolean validId(String id) { return MAIN.equals(id) || id != null && id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"); }
    private void write(String id, JsonObject value) throws IOException {
        AtomicFileWriter.write(file(id), writer -> writer.write(SharedConfigCodec.encode(value)));
        cache.put(id, value.deepCopy());
    }
    private void saveIndex() throws IOException {
        var json = new JsonObject(); json.addProperty("active", active);
        json.add("profiles", new Gson().toJsonTree(entries.values().stream().filter(e -> !MAIN.equals(e.id())).toList()));
        AtomicFileWriter.write(directory.resolve("index.json"), writer -> writer.write(json.toString()));
    }
}
