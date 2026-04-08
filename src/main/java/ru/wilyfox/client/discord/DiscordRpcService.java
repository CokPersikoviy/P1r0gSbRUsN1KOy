package ru.wilyfox.client.discord;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import ru.wilyfox.client.boss.BossDamageInfo;
import ru.wilyfox.client.boss.BossDamageStore;
import ru.wilyfox.client.combo.ComboProgressStore;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.DiscordRpcConfig;
import ru.wilyfox.client.level.LevelProgressStore;
import ru.wilyfox.client.protocol.CurrentServerInfo;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.protocol.DwGameEvent;
import ru.wilyfox.utils.Formatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.debug;
import static ru.wilyfox.client.debug.DebugLogger.error;

public final class DiscordRpcService {
    private static final String APPLICATION_ID = "1490415144134770830";
    private static final int CAPACITY_RETRY_ATTEMPTS = 3;
    private static final long CAPACITY_RETRY_DELAY_MS = 1500L;
    private static final long DEFAULT_UPDATE_INTERVAL_MS = 5000L;
    private static final long BOSS_ACTIVITY_TIMEOUT_MS = 20_000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "froghelper-discord-rpc");
        thread.setDaemon(true);
        return thread;
    });

    private static final Object LOCK = new Object();
    private static final AtomicBoolean AUTO_UPDATE_SCHEDULED = new AtomicBoolean(false);
    private static final AtomicReference<PresenceData> PENDING_AUTO_PRESENCE = new AtomicReference<>();

    private static DiscordIpcClient client;
    private static String activeClientId = "";
    private static long startedAtMillis;
    private static long lastAutoUpdateAt;
    private static String lastPresenceSignature = "";
    private static boolean registered;
    private static volatile String status = "Stopped";
    private static volatile Mode mode = Mode.NONE;

    private static LevelProgressStore levelProgressStore;
    private static ComboProgressStore comboProgressStore;
    private static BossDamageStore bossDamageStore;

    private DiscordRpcService() {
    }

    public static void bindLevelProgressStore(LevelProgressStore store) {
        levelProgressStore = store;
    }

    public static void bindComboProgressStore(ComboProgressStore store) {
        comboProgressStore = store;
    }

    public static void bindBossDamageStore(BossDamageStore store) {
        bossDamageStore = store;
    }

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (mode == Mode.AUTO) {
                EXECUTOR.execute(DiscordRpcService::stopAutoInternal);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DiscordRpcConfig config = ConfigManager.get().discordRpc;
            if (!config.active) {
                PENDING_AUTO_PRESENCE.set(null);
                if (mode == Mode.AUTO) {
                    EXECUTOR.execute(DiscordRpcService::stopAutoInternal);
                } else if (!"Disabled".equals(status)) {
                    status = "Disabled";
                }
                return;
            }

            if (!shouldRunAuto(client)) {
                PENDING_AUTO_PRESENCE.set(null);
                if (mode == Mode.AUTO) {
                    EXECUTOR.execute(DiscordRpcService::stopAutoInternal);
                }
                return;
            }

            long now = System.currentTimeMillis();
            long intervalMs = Math.max(1L, config.updateIntervalSeconds) * 1000L;
            if (mode == Mode.AUTO && now - lastAutoUpdateAt < intervalMs) {
                return;
            }

            PresenceData presence = buildAutoPresence(client, config);
            if (presence == null) {
                PENDING_AUTO_PRESENCE.set(null);
                if (mode == Mode.AUTO) {
                    EXECUTOR.execute(DiscordRpcService::stopAutoInternal);
                }
                return;
            }

            enqueueAutoPresenceUpdate(presence);
        });
    }

    public static String getStatus() {
        return status;
    }

    private static void enqueueAutoPresenceUpdate(PresenceData presence) {
        PENDING_AUTO_PRESENCE.set(presence);
        if (AUTO_UPDATE_SCHEDULED.compareAndSet(false, true)) {
            EXECUTOR.execute(DiscordRpcService::drainAutoPresenceUpdates);
        }
    }

    private static void drainAutoPresenceUpdates() {
        try {
            while (true) {
                PresenceData presence = PENDING_AUTO_PRESENCE.getAndSet(null);
                if (presence == null) {
                    break;
                }

                updateAutoPresence(presence);
            }
        } finally {
            AUTO_UPDATE_SCHEDULED.set(false);
            if (PENDING_AUTO_PRESENCE.get() != null && AUTO_UPDATE_SCHEDULED.compareAndSet(false, true)) {
                EXECUTOR.execute(DiscordRpcService::drainAutoPresenceUpdates);
            }
        }
    }

    private static void updateAutoPresence(PresenceData presence) {
        long now = System.currentTimeMillis();

        try {
            synchronized (LOCK) {
                if (client == null || !APPLICATION_ID.equals(activeClientId)) {
                    stopSessionLocked();

                    client = connectWithRetry(APPLICATION_ID);
                    activeClientId = APPLICATION_ID;
                    startedAtMillis = now;
                    lastPresenceSignature = "";
                } else if (startedAtMillis <= 0L) {
                    startedAtMillis = now;
                }

                mode = Mode.AUTO;
            }

            PresenceData resolvedPresence = presence.withStartedAtSeconds(
                    presence.includeElapsedTime() ? startedAtMillis / 1000L : 0L
            );

            String signature = resolvedPresence.signature();
            if (signature.equals(lastPresenceSignature) && now - lastAutoUpdateAt < DEFAULT_UPDATE_INTERVAL_MS) {
                status = "Running (Auto)";
                return;
            }

            applyPresence(resolvedPresence);
            lastAutoUpdateAt = now;
            lastPresenceSignature = signature;
            status = "Running (Auto)";
        } catch (Throwable throwable) {
            failAndShutdown("Discord RPC auto update failed", throwable);
        }
    }

    private static PresenceData buildAutoPresence(Minecraft minecraft, DiscordRpcConfig config) {
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        ServerData currentServer = minecraft.getCurrentServer();
        if (currentServer == null || currentServer.ip == null || currentServer.ip.isBlank()) {
            return null;
        }

        if (config.privacyMode) {
            return buildNeutralPresence(config);
        }

        RpcMode rpcMode = resolveMode(minecraft);
        if (rpcMode == RpcMode.NEUTRAL) {
            return buildNeutralPresence(config);
        }
        String details = buildDetails(currentServer, config, rpcMode);
        String state = buildState(currentServer, config, rpcMode);
        String smallImageKey = "";
        String smallImageText = "";

        if (rpcMode != RpcMode.DEATH) {
            DwGameEvent gameEvent = DiamondWorldProtocolClient.getCurrentGameEvent();
            if (config.showGameEvent && gameEvent != null && gameEvent != DwGameEvent.NONE) {
                smallImageKey = "event";
                smallImageText = formatGameEvent(gameEvent);
            }
        }

        if (details.isBlank()) {
            details = "Minecraft";
        }
        if (state.isBlank()) {
            state = "On a server";
        }

        return new PresenceData(
                limit(details, 128),
                limit(state, 128),
                0L,
                rpcMode.largeImageKey(),
                rpcMode.largeImageText(),
                smallImageKey,
                smallImageText,
                config.showElapsedTime
        );
    }

    private static PresenceData buildNeutralPresence(DiscordRpcConfig config) {
        return new PresenceData(
                "FrogHelper",
                "",
                0L,
                "froghelper",
                "FrogHelper",
                "",
                "",
                config.showElapsedTime
        );
    }

    private static String buildDetails(ServerData currentServer, DiscordRpcConfig config, RpcMode rpcMode) {
        String serverName = formatServer(currentServer, config);
        return switch (rpcMode) {
            case NEUTRAL -> "FrogHelper";
            case HUB -> serverName.isBlank() ? "In the hub" : "In the hub of " + serverName;
            case MINE -> serverName.isBlank() ? "Mining" : "Mining on " + serverName;
            case DUNGEON -> "Exploring a dungeon";
            case SIEGE -> "Fighting in a siege";
            case BOSS -> buildBossDetails(serverName);
            case FISHING -> "Fishing";
            case DEATH -> buildDeathDetails();
        };
    }

    private static String buildState(ServerData currentServer, DiscordRpcConfig config, RpcMode rpcMode) {
        List<String> parts = new ArrayList<>();

        switch (rpcMode) {
            case NEUTRAL -> {
            }
            case HUB -> {
                String serverName = formatServer(currentServer, config);
                if (!serverName.isBlank()) {
                    parts.add(serverName);
                }
            }
            case MINE, DUNGEON, SIEGE, FISHING -> addCommonProgress(parts, config);
            case BOSS -> {
                addBossState(parts);
                addCommonProgress(parts, config);
            }
            case DEATH -> {
                parts.add("Waiting to respawn");
                addDeathContext(parts, config);
            }
        }

        return String.join(" • ", parts);
    }

    private static void addCommonProgress(List<String> parts, DiscordRpcConfig config) {
        if (config.showLocation) {
            String location = formatLocation(DiamondWorldProtocolClient.getCurrentGameLocation());
            if (!location.isBlank()) {
                parts.add(location);
            }
        }

        if (config.showLevel && levelProgressStore != null) {
            LevelProgressStore.LevelProgressSnapshot snapshot = levelProgressStore.getSnapshot();
            if (snapshot.available() && snapshot.level() > 0) {
                parts.add("Lvl " + snapshot.level());
            }
        }

        if (config.showCombo && comboProgressStore != null) {
            ComboProgressStore.Snapshot snapshot = comboProgressStore.getSnapshot();
            if (snapshot.available()) {
                parts.add("Combo x" + trimTrailingZeros(snapshot.booster()));
            }
        }

        if (config.showGameEvent) {
            String eventName = formatGameEvent(DiamondWorldProtocolClient.getCurrentGameEvent());
            if (!eventName.isBlank()) {
                parts.add(eventName);
            }
        }
    }

    private static void addBossState(List<String> parts) {
        BossDamageInfo bossDamage = getActiveBossDamage();
        if (bossDamage == null) {
            return;
        }

        String bossName = Formatting.sanitize(bossDamage.bossName());
        if (!bossName.isBlank()) {
            parts.add(bossName);
        }
        if (bossDamage.bossLevel() > 0) {
            parts.add("Boss lvl " + bossDamage.bossLevel());
        }
    }

    private static void addDeathContext(List<String> parts, DiscordRpcConfig config) {
        if (isBossMode()) {
            BossDamageInfo bossDamage = getActiveBossDamage();
            if (bossDamage != null) {
                String bossName = Formatting.sanitize(bossDamage.bossName());
                if (!bossName.isBlank()) {
                    parts.add(bossName);
                }
            } else {
                parts.add("Boss fight");
            }
        } else if (config.showLocation) {
            String location = formatLocation(DiamondWorldProtocolClient.getCurrentGameLocation());
            if (!location.isBlank()) {
                parts.add(location);
            }
        }
    }

    private static String buildBossDetails(String serverName) {
        BossDamageInfo bossDamage = getActiveBossDamage();
        if (bossDamage != null) {
            String bossName = Formatting.sanitize(bossDamage.bossName());
            if (!bossName.isBlank()) {
                return "Fighting " + bossName;
            }
        }

        String locationId = normalizeLocationId(DiamondWorldProtocolClient.getCurrentGameLocation());
        String locationName = locationId == null ? "" : DiamondWorldProtocolClient.getFishingLocationName(locationId);
        if (locationName.isBlank()) {
            locationName = formatLocation(DiamondWorldProtocolClient.getCurrentGameLocation());
        }
        if (!locationName.isBlank()) {
            return "Fighting at " + locationName;
        }

        return serverName.isBlank() ? "Fighting a boss" : "Boss fight on " + serverName;
    }

    private static String buildDeathDetails() {
        return isBossMode() ? "Down in a boss fight" : "Waiting to respawn";
    }

    private static String formatServer(ServerData currentServer, DiscordRpcConfig config) {
        CurrentServerInfo serverInfo = DiamondWorldProtocolClient.getCurrentServerInfo();
        if (config.showServer && serverInfo.isKnown()) {
            if ("PRISONEVO".equals(serverInfo.family())) {
                String base = serverInfo.serverNumber() > 0 ? "PrisonEvo-" + serverInfo.serverNumber() : "PrisonEvo";
                if (config.showMirror && serverInfo.mirror() > 0) {
                    return base + " #" + serverInfo.mirror();
                }
                return base;
            }

            if ("HUB".equals(serverInfo.family())) {
                return "Hub";
            }

            return limit(serverInfo.displayName(), 128);
        }

        String serverName = trimToEmpty(currentServer.name);
        if (!serverName.isBlank()) {
            return serverName;
        }

        return trimToEmpty(currentServer.ip);
    }

    private static String formatLocation(String locationId) {
        String normalizedLocationId = normalizeLocationId(locationId);
        if (normalizedLocationId != null) {
            String protocolName = DiamondWorldProtocolClient.getFishingLocationName(normalizedLocationId);
            if (!protocolName.isBlank() && !protocolName.equals(normalizedLocationId)) {
                return Formatting.sanitize(protocolName);
            }
        }

        String sanitized = Formatting.sanitize(locationId);
        if (sanitized.isBlank()) {
            return "";
        }

        String[] words = sanitized.replace('_', ' ').split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private static String formatGameEvent(DwGameEvent gameEvent) {
        if (gameEvent == null || gameEvent == DwGameEvent.NONE) {
            return "";
        }

        return Formatting.sanitize(gameEvent.displayName());
    }

    private static RpcMode resolveMode(Minecraft minecraft) {
        String locationId = normalizeLocationId(DiamondWorldProtocolClient.getCurrentGameLocation());
        if (isDeathMode(minecraft)) {
            return RpcMode.DEATH;
        }
        if (isBossMode()) {
            return RpcMode.BOSS;
        }
        RpcMode exactMode = DiscordLocationRegistry.resolveExact(locationId);
        if (exactMode != null) {
            return exactMode;
        }
        if (DiamondWorldProtocolClient.isDungeonLocation()) {
            return RpcMode.DUNGEON;
        }
        if (DiamondWorldProtocolClient.isSiegeLocation()) {
            return RpcMode.SIEGE;
        }
        if (looksLikeFishingLocation(locationId)) {
            return RpcMode.FISHING;
        }
        if (looksLikeBossLocation(locationId)) {
            return RpcMode.BOSS;
        }

        CurrentServerInfo serverInfo = DiamondWorldProtocolClient.getCurrentServerInfo();
        if ("HUB".equals(serverInfo.family())) {
            return RpcMode.HUB;
        }

        if (looksLikeHubLocation(locationId)) {
            return RpcMode.HUB;
        }

        if (looksLikeMineLocation(locationId)) {
            return RpcMode.MINE;
        }

        return RpcMode.NEUTRAL;
    }

    private static boolean looksLikeFishingLocation(String locationId) {
        if (locationId == null) {
            return false;
        }

        if (DiamondWorldProtocolClient.getFishingLocationIds().contains(locationId)) {
            return true;
        }

        return locationId.contains("fish") || locationId.contains("fishing");
    }

    private static boolean looksLikeBossLocation(String locationId) {
        if (locationId == null) {
            return false;
        }

        if (DiscordLocationRegistry.isBoss(locationId)) {
            return true;
        }

        return locationId.contains("boss")
                || locationId.contains("kriger")
                || locationId.contains("guardian");
    }

    private static boolean looksLikeHubLocation(String locationId) {
        if (locationId == null) {
            return false;
        }

        if (DiscordLocationRegistry.isHub(locationId)) {
            return true;
        }

        return locationId.contains("hub")
                || locationId.contains("spawn")
                || locationId.contains("lobby");
    }

    private static boolean looksLikeMineLocation(String locationId) {
        if (locationId == null) {
            return false;
        }

        return locationId.contains("mine")
                || locationId.contains("shaft")
                || locationId.contains("quarry")
                || locationId.contains("cave");
    }

    private static String normalizeLocationId(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static boolean isBossMode() {
        return getActiveBossDamage() != null;
    }

    private static BossDamageInfo getActiveBossDamage() {
        if (bossDamageStore == null || !bossDamageStore.hasActiveEntry()) {
            return null;
        }

        BossDamageInfo info = bossDamageStore.getCurrent();
        if (info == null) {
            return null;
        }

        long age = System.currentTimeMillis() - info.updatedAt();
        return age <= BOSS_ACTIVITY_TIMEOUT_MS ? info : null;
    }

    private static boolean isDeathMode(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }

        if (!minecraft.player.isAlive() || minecraft.player.isDeadOrDying() || minecraft.player.isSpectator()) {
            return true;
        }

        if (minecraft.gameMode != null) {
            GameType mode = minecraft.gameMode.getPlayerMode();
            if (mode == GameType.ADVENTURE && isBossMode()) {
                return true;
            }
        }

        return false;
    }

    private static void applyPresence(PresenceData presence) throws IOException {
        DiscordIpcClient localClient;
        synchronized (LOCK) {
            localClient = client;
        }

        if (localClient == null) {
            throw new IOException("Discord IPC is not connected");
        }

        localClient.setActivity(
                presence.details(),
                presence.state(),
                presence.startedAtSeconds(),
                presence.largeImageKey(),
                presence.largeImageText(),
                presence.smallImageKey(),
                presence.smallImageText()
        );
    }

    private static DiscordIpcClient connectWithRetry(String clientId) throws IOException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= CAPACITY_RETRY_ATTEMPTS; attempt++) {
            DiscordIpcClient localClient = new DiscordIpcClient();
            try {
                localClient.connect(clientId);
                if (attempt > 1) {
                    status = "Connected after retry";
                }
                return localClient;
            } catch (IOException exception) {
                lastFailure = exception;
                try {
                    localClient.close();
                } catch (IOException closeException) {
                    debug(LOGGER, "Discord RPC retry shutdown failed", closeException);
                }

                if (!isCapacityError(exception) || attempt == CAPACITY_RETRY_ATTEMPTS) {
                    break;
                }

                status = "Discord is busy, retry " + attempt + "/" + CAPACITY_RETRY_ATTEMPTS;
                sleepQuietly(CAPACITY_RETRY_DELAY_MS);
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }

        throw new IOException("Discord RPC connection failed");
    }

    private static void stopAutoInternal() {
        PENDING_AUTO_PRESENCE.set(null);
        if (mode != Mode.AUTO) {
            return;
        }

        synchronized (LOCK) {
            stopSessionLocked();
        }
        status = "Idle";
    }

    private static void stopSessionLocked() {
        if (client != null) {
            try {
                client.clearActivity();
            } catch (IOException exception) {
                debug(LOGGER, "Discord RPC clear activity failed", exception);
            }

            try {
                client.close();
            } catch (IOException exception) {
                debug(LOGGER, "Discord RPC shutdown failed", exception);
            }
        }

        client = null;
        activeClientId = "";
        startedAtMillis = 0L;
        lastAutoUpdateAt = 0L;
        lastPresenceSignature = "";
        mode = Mode.NONE;
    }

    private static boolean shouldRunAuto(Minecraft client) {
        return client != null
                && client.player != null
                && client.getConnection() != null
                && client.getCurrentServer() != null;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimTrailingZeros(double value) {
        String formatted = String.format(Locale.US, "%.2f", value);
        while (formatted.contains(".") && (formatted.endsWith("0") || formatted.endsWith("."))) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)).trim() + "...";
    }

    private static void failAndShutdown(String logMessage, Throwable throwable) {
        String summary = formatThrowable(throwable);
        status = summary;
        error(LOGGER, "{}: {}", logMessage, summary, throwable);
        synchronized (LOCK) {
            stopSessionLocked();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isCapacityError(IOException exception) {
        String message = exception.getMessage();
        return message != null && message.contains("1006");
    }

    private static String formatThrowable(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }

        return root.getClass().getSimpleName() + ": " + message;
    }

    private enum Mode {
        NONE,
        AUTO
    }

    enum RpcMode {
        NEUTRAL("froghelper", "FrogHelper"),
        HUB("hub", "Hub"),
        MINE("mine", "Mine"),
        DUNGEON("dungeon", "Dungeon"),
        SIEGE("siege", "Siege"),
        BOSS("boss", "Boss fight"),
        FISHING("fishing", "Fishing"),
        DEATH("death", "Waiting to respawn");

        private final String largeImageKey;
        private final String largeImageText;

        RpcMode(String largeImageKey, String largeImageText) {
            this.largeImageKey = largeImageKey;
            this.largeImageText = largeImageText;
        }

        private String largeImageKey() {
            return largeImageKey;
        }

        private String largeImageText() {
            return largeImageText;
        }
    }

    private record PresenceData(
            String details,
            String state,
            long startedAtSeconds,
            String largeImageKey,
            String largeImageText,
            String smallImageKey,
            String smallImageText,
            boolean includeElapsedTime
    ) {
        private PresenceData withStartedAtSeconds(long startedAtSeconds) {
            return new PresenceData(
                    details,
                    state,
                    startedAtSeconds,
                    largeImageKey,
                    largeImageText,
                    smallImageKey,
                    smallImageText,
                    includeElapsedTime
            );
        }

        private String signature() {
            return details + "|" + state + "|" + startedAtSeconds + "|" + largeImageKey + "|" + largeImageText
                    + "|" + smallImageKey + "|" + smallImageText;
        }
    }
}
