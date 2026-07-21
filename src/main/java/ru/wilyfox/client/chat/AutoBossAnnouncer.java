package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.boss.BossRepository;
import ru.wilyfox.bridge.BossHealthOverlayAccessor;
import ru.wilyfox.client.hud.config.BossRespawnMessagesConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.utils.BossName;
import ru.wilyfox.utils.BossLevel;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutoBossAnnouncer {
    private static final Pattern BOSS_CURSED_PATTERN = Pattern.compile("Босс проклят! Особенность: ([А-Яа-яЁё ]+)");
    // DW boss-bar title is "<name> <number>❤" (heart U+2764, no space before it). Trailing \D* tolerates
    // the heart and any other non-digit suffix (e.g. cursed markers) — the old "(?:\s+.*)?" needed a space
    // before the trailing text, so "<number>❤" failed to match and both HP and name detection broke.
    private static final Pattern BOSS_HEALTH_PATTERN = Pattern.compile("^(.+?)\\s+(\\d+(?:\\.\\d+)?)\\D*$");
    private static final long SPAWN_ANNOUNCE_WINDOW_MS = 2_000L;
    private static final long RESPAWN_RESET_GRACE_MS = 5_000L;
    private static final DecimalFormat HEALTH_FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private static final Map<String, Long> announcedRespawns = new HashMap<>();
    private static final Map<String, Long> announcedSpawns = new HashMap<>();
    private static final Map<String, Long> lowHealthAnnouncements = new HashMap<>();

    private static BossRepository repository;
    private static boolean initialized = false;

    private AutoBossAnnouncer() {
    }

    public static void bindRepository(BossRepository bossRepository) {
        repository = bossRepository;
    }

    public static void register() {
        if (initialized) {
            return;
        }

        initialized = true;

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearState());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("tick/AutoBossAnnouncer")) {
                if (repository == null || client.player == null || client.player.connection == null) {
                    return;
                }

                checkRespawnMessages();
                checkLowHealthMessages();
            }
        });
    }

    public static void onIncomingMessage(Component component) {
        if (component == null || !ConfigManager.get().bossRespawnMessages.curseMessage) {
            return;
        }

        Matcher matcher = BOSS_CURSED_PATTERN.matcher(component.getString());
        if (!matcher.find()) {
            return;
        }

        BossBarSnapshot snapshot = getCurrentBossBarSnapshot();
        if (snapshot == null) {
            return;
        }

        showLocalMessage(formatBossLabel(snapshot.name(), snapshot.level()) + " проклят: " + matcher.group(1));
    }

    private static void checkRespawnMessages() {
        BossRespawnMessagesConfig config = ConfigManager.get().bossRespawnMessages;
        long now = System.currentTimeMillis();
        Set<String> activeKeys = new HashSet<>();

        for (BossInfo boss : repository.getAllMerged()) {
            String bossKey = bossKey(boss);
            long respawnAt = boss.getRespawnAt();
            long remaining = respawnAt - now;

            activeKeys.add(bossKey);
            resetRespawnAnnouncementIfNewCycle(config, bossKey, remaining);
            resetSpawnAnnouncementIfNewCycle(bossKey, remaining);

            if (config.preRespawnMessage
                    && config.preRespawnSeconds > 0
                    && remaining > 0L
                    && remaining <= config.preRespawnSeconds * 1000L
                    && !announcedRespawns.containsKey(bossKey)) {
                showLocalMessage(formatBossLabel(boss.getName(), boss.getLevel()) + " возродится через " + formatDuration(remaining));
                announcedRespawns.put(bossKey, respawnAt);
            }

            if (config.spawnMessage
                    && remaining <= 0L
                    && remaining >= -SPAWN_ANNOUNCE_WINDOW_MS
                    && !announcedSpawns.containsKey(bossKey)) {
                showLocalMessage(formatBossLabel(boss.getName(), boss.getLevel()) + " возродился");
                announcedSpawns.put(bossKey, respawnAt);
            }
        }

        announcedRespawns.entrySet().removeIf(entry -> !activeKeys.contains(entry.getKey()));
        announcedSpawns.entrySet().removeIf(entry -> !activeKeys.contains(entry.getKey()));
    }

    private static void checkLowHealthMessages() {
        BossRespawnMessagesConfig config = ConfigManager.get().bossRespawnMessages;
        if (!config.lowHealthMessage) {
            return;
        }

        BossBarSnapshot snapshot = getCurrentBossBarSnapshot();
        if (snapshot == null || snapshot.percent() > config.lowHealthPercent) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(1, config.lowHealthCooldownSeconds) * 1000L;
        String bossKey = snapshot.name().trim().toLowerCase(Locale.ROOT) + "#" + snapshot.level();
        long lastSent = lowHealthAnnouncements.getOrDefault(bossKey, 0L);
        if (now - lastSent < cooldownMs) {
            return;
        }

        // The absolute HP number is best-effort; when the title didn't yield one, report the percent only.
        String healthText = snapshot.health() >= 0.0d
                ? " осталось " + HEALTH_FORMAT.format(snapshot.health()) + " HP (" + Math.round(snapshot.percent()) + "%)"
                : " осталось " + Math.round(snapshot.percent()) + "% HP";

        showLocalMessage(formatServerPrefix() + formatBossLabel(snapshot.name(), snapshot.level()) + healthText);
        lowHealthAnnouncements.put(bossKey, now);
    }

    private static BossBarSnapshot getCurrentBossBarSnapshot() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null) {
            return null;
        }

        BossHealthOverlay overlay = minecraft.gui.getBossOverlay();
        if (!(overlay instanceof BossHealthOverlayAccessor accessor)) {
            return null;
        }

        List<LerpingBossEvent> events = accessor.froghelper$getEvents();
        for (int index = 0; index < events.size(); index++) {
            // Index 0 is always reserved by the server for a status line (player info etc.), never a boss.
            if (index == 0) {
                continue;
            }

            LerpingBossEvent event = events.get(index);
            String rawTitle = event.getName().getString().trim();

            // The absolute HP number is best-effort: if the title format doesn't yield one we still
            // proceed. Detection relies on the bar fill (getProgress) below, so a title that doesn't
            // match this pattern must not suppress the low-health alert (it did before).
            String nameCandidate = rawTitle;
            double health = -1.0d;
            Matcher matcher = BOSS_HEALTH_PATTERN.matcher(rawTitle);
            if (matcher.find()) {
                nameCandidate = matcher.group(1);
                health = parseDouble(matcher.group(2));
            }

            String bossName = normalizeBossBarName(nameCandidate);
            Integer level = BossLevel.getBossLevel(bossName);
            if (level == null) {
                String normalizedBossName = BossName.getBossName(bossName.toUpperCase(Locale.ROOT));
                if (normalizedBossName != null) {
                    bossName = normalizedBossName;
                    level = BossLevel.getBossLevel(bossName);
                }
            }
            if (level == null) {
                continue;
            }

            double percent = Math.max(0.0d, Math.min(100.0d, event.getProgress() * 100.0d));
            return new BossBarSnapshot(bossName, level, health, percent);
        }

        return null;
    }

    private static void resetAnnouncementsIfRespawnChanged(Map<String, Long> storage, String bossKey, long respawnAt) {
        Long announcedRespawn = storage.get(bossKey);
        if (announcedRespawn != null && announcedRespawn != respawnAt) {
            storage.remove(bossKey);
        }
    }

    private static void resetRespawnAnnouncementIfNewCycle(BossRespawnMessagesConfig config, String bossKey, long remaining) {
        if (!announcedRespawns.containsKey(bossKey)) {
            return;
        }

        long threshold = Math.max(1, config.preRespawnSeconds) * 1000L + RESPAWN_RESET_GRACE_MS;
        if (remaining > threshold) {
            announcedRespawns.remove(bossKey);
        }
    }

    private static void resetSpawnAnnouncementIfNewCycle(String bossKey, long remaining) {
        if (!announcedSpawns.containsKey(bossKey)) {
            return;
        }

        if (remaining > SPAWN_ANNOUNCE_WINDOW_MS + RESPAWN_RESET_GRACE_MS) {
            announcedSpawns.remove(bossKey);
        }
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return -1.0d;
        }
    }

    private static String formatBossLabel(String bossName, int level) {
        return bossName + " [" + level + "]";
    }

    private static String normalizeBossBarName(String rawBossName) {
        String normalized = rawBossName == null ? "" : rawBossName.trim();
        if (normalized.startsWith("Босс ")) {
            normalized = normalized.substring(5).trim();
        }
        return normalized;
    }

    private static String formatServerPrefix() {
        String serverName = DiamondWorldProtocolClient.getCurrentServerDisplayName(null);
        return serverName == null || serverName.isBlank() ? "" : "[" + serverName + "] ";
    }

    private static String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0L, millis));
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0L) {
            return hours + "ч " + String.format(Locale.ROOT, "%02dм %02dс", minutes, seconds);
        }
        if (minutes > 0L) {
            return minutes + "м " + String.format(Locale.ROOT, "%02dс", seconds);
        }
        return seconds + "с";
    }

    private static void showLocalMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.connection != null) {
            ChatDispatchQueue.enqueueChat("@" + message, 1_000L);
        } else if (minecraft.gui != null) {
            minecraft.gui.getChat().addMessage(Component.literal("@" + message));
        }
    }

    private static String bossKey(BossInfo boss) {
        return boss.getName().trim().toLowerCase(Locale.ROOT) + "#" + boss.getLevel();
    }

    private static void clearState() {
        announcedRespawns.clear();
        announcedSpawns.clear();
        lowHealthAnnouncements.clear();
    }

    private record BossBarSnapshot(String name, int level, double health, double percent) {
    }
}
