package ru.wilyfox.client.moduser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.clan.PlayerClanChatParser;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.error;
import static ru.wilyfox.client.debug.DebugLogger.info;

/**
 * Tracks which players run FrogHelper, discovered peer-to-peer through chat: outgoing chat carries an
 * invisible marker ({@link #MARKER}), and any player whose message carries it is recorded here — kept
 * persistently in {@code config/froghelper-mod-users.json}. Because a long message can hit the server's
 * chat length limit and get the trailing marker truncated, a known user is only dropped after
 * {@code MISS_LIMIT} consecutive marker-less messages (a single miss is treated as truncation).
 */
public final class ModUserStorage {
    /** U+24BB visible beacon that DiamondWorld chat filter passes (invisible/PUA chars get stripped). */
    public static final String MARKER = "Ⓕ"; // U+24BB visible beacon; DW strips invisible/PUA
    private static final int MISS_LIMIT = 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORAGE_PATH = FabricLoader.getInstance().getConfigDir().resolve("froghelper-mod-users.json");

    private static final Set<String> KNOWN = new LinkedHashSet<>();      // normalized names
    private static final Map<String, String> DISPLAY = new HashMap<>();  // normalized -> display name
    private static final Map<String, Integer> MISS_STREAK = new HashMap<>();

    private static boolean loaded;

    private ModUserStorage() {
    }

    public static synchronized void init() {
        ensureLoaded();
    }

    /** Inspect one incoming chat line: if it's a player message, mark/unmark that player as a mod user. */
    public static synchronized void captureFromChat(Component component) {
        if (component == null) {
            return;
        }

        String raw = component.getString();
        if (raw.contains("{fh")) {
            return; // FrogHelper protocol line ({fhmu:/{fhb:/{fhping:) — not a normal player message
        }

        String sender = PlayerClanChatParser.senderNameLenient(component);
        if (sender == null) {
            return; // not a parseable player chat line (system message, etc.)
        }

        if (isSelf(sender)) {
            return; // our own echoed message — no need to record ourselves
        }

        onPlayerChat(sender, raw.contains(MARKER));
    }

    private static void onPlayerChat(String name, boolean hasMarker) {
        String key = normalize(name);
        if (key == null) {
            return;
        }

        ensureLoaded();

        if (hasMarker) {
            MISS_STREAK.remove(key);
            DISPLAY.put(key, name);
            if (KNOWN.add(key)) {
                info(LOGGER, "FrogHelper mod user detected: {}", name);
                save();
            }
            ModUserProtocol.onModUserSeen(name); // silent PM pairing + list sync (no-op if mesh disabled)
            return;
        }

        if (!KNOWN.contains(key)) {
            return; // a non-mod user's messages never carry the marker — nothing to track
        }

        int misses = MISS_STREAK.merge(key, 1, Integer::sum);
        if (misses >= MISS_LIMIT) {
            KNOWN.remove(key);
            DISPLAY.remove(key);
            MISS_STREAK.remove(key);
            info(LOGGER, "FrogHelper mod user dropped after {} marker-less messages: {}", MISS_LIMIT, name);
            save();
        }
    }

    public static synchronized boolean isKnown(String playerName) {
        String key = normalize(playerName);
        if (key == null) {
            return false;
        }
        ensureLoaded();
        return KNOWN.contains(key);
    }

    public static synchronized int knownCount() {
        ensureLoaded();
        return KNOWN.size();
    }

    /** Record a player as a known mod user (e.g. the sender of an incoming FrogHelper protocol message). */
    public static synchronized void markKnown(String playerName) {
        String key = normalize(playerName);
        if (key == null) {
            return;
        }
        ensureLoaded();
        MISS_STREAK.remove(key);
        DISPLAY.put(key, playerName.trim());
        if (KNOWN.add(key)) {
            info(LOGGER, "FrogHelper mod user recorded: {}", playerName);
            save();
        }
    }

    /** Merge a batch of known mod-user names (from a mesh sync). Returns how many were newly added. */
    public static synchronized int merge(List<String> names) {
        if (names == null || names.isEmpty()) {
            return 0;
        }
        ensureLoaded();
        int added = 0;
        for (String name : names) {
            String key = normalize(name);
            if (key == null) {
                continue;
            }
            DISPLAY.put(key, name.trim());
            if (KNOWN.add(key)) {
                added++;
            }
        }
        if (added > 0) {
            info(LOGGER, "FrogHelper mesh sync added {} mod users", added);
            save();
        }
        return added;
    }

    /** Snapshot of known mod-user display names (for sending in a mesh sync). */
    public static synchronized List<String> knownDisplayNames() {
        ensureLoaded();
        return new ArrayList<>(DISPLAY.values());
    }

    private static boolean isSelf(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && name.equalsIgnoreCase(minecraft.player.getGameProfile().getName());
    }

    private static String normalize(String name) {
        if (name == null) {
            return null;
        }
        String cleaned = name.trim();
        return cleaned.isBlank() ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        KNOWN.clear();
        DISPLAY.clear();

        if (!Files.exists(STORAGE_PATH)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(STORAGE_PATH)) {
            ModUsersFile file = GSON.fromJson(reader, ModUsersFile.class);
            if (file == null || file.names == null) {
                return;
            }
            for (String name : file.names) {
                String key = normalize(name);
                if (key != null) {
                    KNOWN.add(key);
                    DISPLAY.put(key, name.trim());
                }
            }
        } catch (Exception exception) {
            error(LOGGER, "Failed to load FrogHelper mod-user storage from {}", STORAGE_PATH, exception);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(STORAGE_PATH.getParent());
            ModUsersFile file = new ModUsersFile();
            file.names = new ArrayList<>(new LinkedHashSet<>(DISPLAY.values()));
            try (Writer writer = Files.newBufferedWriter(STORAGE_PATH)) {
                GSON.toJson(file, writer);
            }
        } catch (Exception exception) {
            error(LOGGER, "Failed to save FrogHelper mod-user storage to {}", STORAGE_PATH, exception);
        }
    }

    private static final class ModUsersFile {
        private List<String> names = new ArrayList<>();
    }
}
