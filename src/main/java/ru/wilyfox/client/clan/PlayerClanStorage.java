package ru.wilyfox.client.clan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.error;

public final class PlayerClanStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, PlayerClanEntry> ENTRIES = new LinkedHashMap<>();

    private static boolean loaded;

    private PlayerClanStorage() {
    }

    public static synchronized void init() {
        ensureLoaded();
    }

    public static synchronized void captureFromChat(Component component) {
        ParsedClanChatEntry parsed = PlayerClanChatParser.parse(component);
        if (parsed == null) {
            return;
        }

        update(parsed.playerName(), parsed.clanName());
    }

    public static synchronized String getClan(String playerName) {
        String normalized = normalize(playerName);
        if (normalized == null) {
            return null;
        }

        ensureLoaded();
        PlayerClanEntry entry = ENTRIES.get(normalized);
        if (entry == null || entry.clanName == null || entry.clanName.isBlank()) {
            return null;
        }

        return entry.clanName;
    }

    public static synchronized void update(String playerName, String clanName) {
        String cleanName = cleanPlayerName(playerName);
        String normalized = normalize(cleanName);
        if (normalized == null) {
            return;
        }

        ensureLoaded();
        if (!updateEntry(ENTRIES, cleanName, clanName, System.currentTimeMillis())) {
            return;
        }
        save();
    }

    static boolean updateEntry(
            Map<String, PlayerClanEntry> entries,
            String playerName,
            String clanName,
            long updatedAt
    ) {
        String cleanName = cleanPlayerName(playerName);
        String normalized = normalize(cleanName);
        if (normalized == null) {
            return false;
        }

        String cleanClan = cleanClan(clanName);
        PlayerClanEntry existing = entries.get(normalized);
        if (existing != null
                && equalsNullable(existing.playerName, cleanName)
                && equalsNullable(existing.clanName, cleanClan)) {
            return false;
        }

        PlayerClanEntry entry = existing != null ? existing : new PlayerClanEntry();
        entry.playerName = cleanName;
        entry.clanName = cleanClan;
        entry.updatedAt = updatedAt;
        entries.put(normalized, entry);
        return true;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        loaded = true;
        ENTRIES.clear();

        Path storagePath = storagePath();
        if (!Files.exists(storagePath)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(storagePath)) {
            PlayerClanStorageFile file = GSON.fromJson(reader, PlayerClanStorageFile.class);
            if (file == null || file.entries == null) {
                return;
            }

            for (Map.Entry<String, PlayerClanEntry> entry : file.entries.entrySet()) {
                PlayerClanEntry value = entry.getValue();
                String normalized = normalize(entry.getKey());
                String cleanName = value != null ? cleanPlayerName(value.playerName) : null;
                if (normalized == null || cleanName == null) {
                    continue;
                }

                PlayerClanEntry sanitized = new PlayerClanEntry();
                sanitized.playerName = cleanName;
                sanitized.clanName = value != null ? cleanClan(value.clanName) : null;
                sanitized.updatedAt = value != null ? value.updatedAt : 0L;
                ENTRIES.put(normalized, sanitized);
            }
        } catch (Exception exception) {
            error(LOGGER, "Failed to load FrogHelper clan storage from {}", storagePath, exception);
        }
    }

    private static void save() {
        Path storagePath = storagePath();
        try {
            Files.createDirectories(storagePath.getParent());

            PlayerClanStorageFile file = new PlayerClanStorageFile();
            for (Map.Entry<String, PlayerClanEntry> entry : ENTRIES.entrySet()) {
                file.entries.put(entry.getKey(), entry.getValue());
            }

            try (Writer writer = Files.newBufferedWriter(storagePath)) {
                GSON.toJson(file, writer);
            }
        } catch (Exception exception) {
            error(LOGGER, "Failed to save FrogHelper clan storage to {}", storagePath, exception);
        }
    }

    private static Path storagePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("froghelper-player-clans.json");
    }

    private static boolean equalsNullable(String left, String right) {
        if (left == null) {
            return right == null;
        }

        return left.equals(right);
    }

    private static String cleanPlayerName(String playerName) {
        if (playerName == null) {
            return null;
        }

        String cleaned = playerName.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String cleanClan(String clanName) {
        if (clanName == null) {
            return null;
        }

        String cleaned = clanName.trim().replace('\u00A0', ' ');
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String normalize(String playerName) {
        String cleaned = cleanPlayerName(playerName);
        if (cleaned == null) {
            return null;
        }

        return cleaned.toLowerCase(Locale.ROOT);
    }
}
