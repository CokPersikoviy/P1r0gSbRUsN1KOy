package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.boss.BossRepository;
import ru.wilyfox.bridge.BossHealthOverlayAccessor;
import ru.wilyfox.client.hud.config.BossRespawnMessagesConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.popup.PopUpManager;
import ru.wilyfox.client.popup.PopUpRequest;
import ru.wilyfox.client.popup.PopUpSeverity;
import ru.wilyfox.client.popup.PopUpSource;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.protocol.DwBossType;

import java.time.Duration;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static ru.wilyfox.FrogHelper.LOGGER;

public final class AutoBossAnnouncer {
    private static final int CURSED_BAR_COLOR = 0x25D192;
    private static final long SPAWN_ANNOUNCE_WINDOW_MS = 2_000L;
    private static final long RESPAWN_RESET_GRACE_MS = 5_000L;

    private static final Map<String, Long> announcedRespawns = new HashMap<>();
    private static final Map<String, Long> announcedSpawns = new HashMap<>();
    private static final Map<String, Long> lowHealthAnnouncements = new HashMap<>();
    private static final Map<UUID, String> rejectedLowHealthBars = new HashMap<>();

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
        // Resolve the formatter and its component/color classes during initialization, before a fight.
        LowHpMessageFormatter.warmUp();

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
        if (component == null) {
            return;
        }

        String text = component.getString();
        BossMessageParser.BossCapture capture = BossMessageParser.parseCapture(text);
        if (capture != null) {
            publishBossCapture(capture);
        }

        BossRespawnMessagesConfig config = ConfigManager.get().bossRespawnMessages;
        if (!config.curseMessage && !config.curseClanMessage) {
            return;
        }

        String curse = BossMessageParser.parseCurse(text);
        DwBossType type = DiamondWorldProtocolClient.getCurrentBossType();
        if (curse == null || type == null) {
            return;
        }

        publishMessage(
                formatServerPrefix() + formatBossLabel(type.name(), type.level()) + " проклят: " + curse,
                config.curseMessage,
                config.curseClanMessage
        );
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

            if ((config.preRespawnMessage || config.preRespawnClanMessage)
                    && config.preRespawnSeconds > 0
                    && remaining > 0L
                    && remaining <= config.preRespawnSeconds * 1000L
                    && !announcedRespawns.containsKey(bossKey)) {
                var message = LowHpMessageFormatter.formatRespawn(config.lowHealthFormat,
                        boss.getName(), boss.getLevel(), "возродится через " + formatDuration(remaining));
                publishMessage(
                        message.component(), message.clanText(),
                        config.preRespawnMessage,
                        config.preRespawnClanMessage
                );
                announcedRespawns.put(bossKey, respawnAt);
            }

            if ((config.spawnMessage || config.spawnClanMessage)
                    && remaining <= 0L
                    && remaining >= -SPAWN_ANNOUNCE_WINDOW_MS
                    && !announcedSpawns.containsKey(bossKey)) {
                var message = LowHpMessageFormatter.formatRespawn(config.lowHealthFormat,
                        boss.getName(), boss.getLevel(), "возродился");
                publishMessage(
                        message.component(), message.clanText(),
                        config.spawnMessage,
                        config.spawnClanMessage
                );
                announcedSpawns.put(bossKey, respawnAt);
            }
        }

        announcedRespawns.entrySet().removeIf(entry -> !activeKeys.contains(entry.getKey()));
        announcedSpawns.entrySet().removeIf(entry -> !activeKeys.contains(entry.getKey()));
    }

    private static void checkLowHealthMessages() {
        BossRespawnMessagesConfig config = ConfigManager.get().bossRespawnMessages;
        if (!config.lowHealthMessage && !config.lowHealthClanMessage) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(1, config.lowHealthCooldownSeconds) * 1000L;
        for (BossBarSnapshot snapshot : getCurrentBossBarSnapshots()) {
            if (snapshot.percent() > config.lowHealthPercent) {
                continue;
            }
            String bossKey = snapshot.name().trim().toLowerCase(Locale.ROOT) + "#" + snapshot.level();
            long lastSent = lowHealthAnnouncements.getOrDefault(bossKey, 0L);
            if (now - lastSent < cooldownMs) {
                continue;
            }

            var message = LowHpMessageFormatter.format(config.lowHealthFormat,
                    DiamondWorldProtocolClient.getCurrentServerDisplayName(null), snapshot.name(), snapshot.level(),
                    snapshot.health(), snapshot.percent(), snapshot.cursed(), snapshot.label());
            if (message.isEmpty()) continue;
            publishMessage(message.component(), message.clanText(),
                    config.lowHealthMessage,
                    config.lowHealthClanMessage
            );
            lowHealthAnnouncements.put(bossKey, now);
        }
    }

    private static List<BossBarSnapshot> getCurrentBossBarSnapshots() {
        if (!DiamondWorldProtocolClient.isCurrentBossLocation()) {
            return List.of();
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null) {
            return List.of();
        }

        BossHealthOverlay overlay = minecraft.gui.hud.getBossOverlay();
        if (!(overlay instanceof BossHealthOverlayAccessor accessor)) {
            return List.of();
        }

        List<LerpingBossEvent> events = accessor.froghelper$getEvents();
        rejectedLowHealthBars.keySet().retainAll(events.stream().map(LerpingBossEvent::getId).toList());
        List<BossBarSnapshot> snapshots = new ArrayList<>();
        for (LerpingBossEvent event : events) {
            BossMessageParser.BossBarText parsed = BossMessageParser.parseBossBar(event.getName().getString());
            if (parsed == null) {
                logRejectedBossBar(event, "unrecognized-title");
                continue;
            }

            DwBossType type = DiamondWorldProtocolClient.getBossTypeByName(parsed.bossName());
            if (type == null) {
                logRejectedBossBar(event, "unknown-boss-type");
                continue;
            }
            rejectedLowHealthBars.remove(event.getId());
            // Both values originate as floats; avoid 0.2f becoming 20.000000298% at a 20% threshold.
            double percent = Math.max(0.0d, Math.min(100.0d, event.getProgress() * 100.0F));
            snapshots.add(new BossBarSnapshot(type.name(), type.level(), parsed.health(), percent, isCursed(event.getName()), parsed.label()));
        }

        return snapshots;
    }

    private static void logRejectedBossBar(LerpingBossEvent event, String reason) {
        if (reason.equals(rejectedLowHealthBars.put(event.getId(), reason))) return;
        LOGGER.info("Low HP message: skipped boss bar; reason={}, location={}, title='{}'",
                reason, DiamondWorldProtocolClient.getCurrentGameLocation(), event.getName().getString());
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

    private static String formatBossLabel(String bossName, int level) {
        return bossName + " [" + level + "]";
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

    private static void showLocalMessage(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) {
            minecraft.gui.hud.getChat().addClientSystemMessage(message);
        }
    }

    private static void publishMessage(String message, boolean local, boolean clan) {
        publishMessage(Component.literal(message), message, local, clan);
    }

    private static void publishMessage(Component localMessage, String clanMessage, boolean local, boolean clan) {
        if (local) {
            showLocalMessage(localMessage);
        }
        if (clan) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.connection != null) {
                ChatDispatchQueue.enqueueChat("@" + clanMessage, 1_000L);
            }
        }
    }

    private static void publishBossCapture(BossMessageParser.BossCapture capture) {
        DwBossType type = DiamondWorldProtocolClient.getBossTypeByName(capture.bossName());
        if (type == null || capture.clanName().isBlank()) {
            return;
        }

        PopUpManager.getInstance().publish(PopUpRequest.of(
                PopUpSource.BOSS_CAPTURE,
                "Босс " + formatBossLabel(type.name(), type.level()) + " захвачен",
                "кланом " + capture.clanName() + ".",
                PopUpSeverity.INFO
        ));
    }

    private static boolean isCursed(Component component) {
        TextColor color = component.getStyle().getColor();
        if (color != null && color.getValue() == CURSED_BAR_COLOR) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (isCursed(sibling)) {
                return true;
            }
        }
        return false;
    }

    private static String bossKey(BossInfo boss) {
        return boss.getName().trim().toLowerCase(Locale.ROOT) + "#" + boss.getLevel();
    }

    private static void clearState() {
        announcedRespawns.clear();
        announcedSpawns.clear();
        lowHealthAnnouncements.clear();
        rejectedLowHealthBars.clear();
    }

    private record BossBarSnapshot(String name, int level, double health, double percent, boolean cursed, String label) {
    }
}
