package ru.wilyfox.client.protocol;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.rune.RuneSetCooldownStore;

import java.util.Locale;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

final class ProtocolTransport {
    private static final long INITIAL_HANDSHAKE_INTERVAL_MS = 2_000L;
    private static final long HANDSHAKE_REFRESH_INTERVAL_MS = 120_000L;
    private static final long STALE_PROTOCOL_TIMEOUT_MS = 20_000L;
    private static final long STALE_HANDSHAKE_RETRY_INTERVAL_MS = 10_000L;
    private static final long WORLD_REFRESH_MIN_INTERVAL_MS = 1_000L;

    private ProtocolTransport() {
    }

    static void init(ProtocolState state, ProtocolRouter router) {
        if (state.initialized) {
            return;
        }

        state.initialized = true;

        PayloadTypeRegistry.serverboundPlay().register(DwHandshakePayload.TYPE, DwHandshakePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DwEvoPlusPayload.TYPE, DwEvoPlusPayload.STREAM_CODEC);

        ClientPlayNetworking.registerGlobalReceiver(DwEvoPlusPayload.TYPE, (payload, context) -> {
            receivePayload(context.client(), state, router, payload.data());
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset(state));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(state, client, sender));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("tick/ProtocolTransport")) {
                if (!isDiamondWorldConnection(client)) {
                    return;
                }

                long now = System.currentTimeMillis();
                if (shouldRefreshWorld(state, now)) {
                    sendHandshake(null, "respawn");
                    state.worldRefreshPending = false;
                    state.locationBeforeWorldRefresh = null;
                    state.lastHandshakeAt = now;
                    return;
                }
                if (!shouldSendHandshake(state, now)) {
                    return;
                }

                sendHandshake(null, "retry");
                state.lastHandshakeAt = now;
            }
        });
    }

    static void onJoin(ProtocolState state, Minecraft client, PacketSender sender) {
        if (!isDiamondWorldConnection(client)) return;
        // A new play session needs its own handshake even when the previous one was healthy.
        state.receivedEvoPlusPayload = false;
        state.lastPayloadAt = 0L;
        state.worldRefreshPending = false;
        state.locationBeforeWorldRefresh = null;
        sendHandshake(sender, "join");
        state.lastHandshakeAt = System.currentTimeMillis();
    }

    static void onRespawn(ProtocolState state, Minecraft client) {
        if (!isDiamondWorldConnection(client) || state.worldRefreshPending) return;
        // Proxies can switch worlds via Respawn without a new Fabric JOIN. Coalesce
        // their paired respawns and skip the request if fresh location arrives first.
        state.worldRefreshPending = true;
        state.locationBeforeWorldRefresh = state.currentGameLocation;
        ModProfiler.getInstance().recordClientEvent("protocol-world-refresh", "respawn");
    }

    static boolean shouldRefreshWorld(ProtocolState state, long now) {
        if (!state.worldRefreshPending) return false;
        if (!java.util.Objects.equals(state.currentGameLocation, state.locationBeforeWorldRefresh)) {
            state.worldRefreshPending = false;
            state.locationBeforeWorldRefresh = null;
            return false;
        }
        return now - state.lastHandshakeAt >= WORLD_REFRESH_MIN_INTERVAL_MS;
    }

    /** Fabric play receivers already run on the client thread, inside the packet task. */
    static void receivePayload(Minecraft client, ProtocolState state, ProtocolRouter router, byte[] data) {
        ModProfiler.getInstance().recordProtocolPayloadReceived(data.length);
        if (client.isSameThread()) {
            applyPayload(state, router, data);
        } else {
            // Preserve ownership for callers outside Fabric's play receiver contract.
            byte[] owned = data.clone();
            client.execute(() -> applyPayload(state, router, owned));
        }
    }

    private static void applyPayload(ProtocolState state, ProtocolRouter router, byte[] data) {
        String oldLocation = state.currentGameLocation != null ? state.currentGameLocation.id() : null;
        state.receivedEvoPlusPayload = true;
        state.lastPayloadAt = System.currentTimeMillis();
        router.route(state, data);
        String newLocation = state.currentGameLocation != null ? state.currentGameLocation.id() : null;
        if (!java.util.Objects.equals(oldLocation, newLocation)) {
            info(LOGGER, "DW protocol: location changed {} -> {}", oldLocation, newLocation);
            ModProfiler.getInstance().recordClientEvent("location-changed", newLocation);
        }
    }

    private static void reset(ProtocolState state) {
        state.resetRuntimeState();
        ProtocolGraphTelemetry.getInstance().reset();

        if (state.bossRepository != null) {
            state.bossRepository.clearProtocol();
        }
        if (state.activeRunesStore != null) {
            state.activeRunesStore.clear();
        }
        if (state.activePetsStore != null) {
            state.activePetsStore.clear();
        }
        if (state.activeMinersStore != null) {
            state.activeMinersStore.clear();
        }
        if (state.abilityCooldownStore != null) {
            state.abilityCooldownStore.clear();
        }
        if (state.bossDamageStore != null) {
            state.bossDamageStore.clear();
        }
        if (state.levelProgressStore != null) {
            state.levelProgressStore.clear();
        }
        if (state.dailyBlocksStore != null) {
            state.dailyBlocksStore.clear();
        }
        if (state.potionStore != null) {
            state.potionStore.clear();
        }
        if (state.sellerCooldownStore != null) {
            state.sellerCooldownStore.clear();
        }
        if (state.comboProgressStore != null) {
            state.comboProgressStore.clear();
        }
        if (state.boosterStore != null) {
            state.boosterStore.clear();
        }
        if (state.wandCooldownTracker != null) {
            state.wandCooldownTracker.clear();
        }

        RuneSetCooldownStore.clear();
    }

    private static boolean isDiamondWorldConnection(Minecraft client) {
        if (client.getConnection() == null || client.player == null) {
            return false;
        }

        ServerData currentServer = client.getCurrentServer();
        if (currentServer == null || currentServer.ip == null) {
            return false;
        }

        return currentServer.ip.toLowerCase(Locale.ROOT).contains("diamondworld");
    }

    private static boolean shouldSendHandshake(ProtocolState state, long now) {
        if (!state.receivedEvoPlusPayload) {
            return now - state.lastHandshakeAt >= INITIAL_HANDSHAKE_INTERVAL_MS;
        }

        if (now - state.lastHandshakeAt >= HANDSHAKE_REFRESH_INTERVAL_MS) {
            return true;
        }

        return now - state.lastPayloadAt >= STALE_PROTOCOL_TIMEOUT_MS
                && now - state.lastHandshakeAt >= STALE_HANDSHAKE_RETRY_INTERVAL_MS;
    }

    private static void sendHandshake(PacketSender sender, String reason) {
        ModProfiler.getInstance().recordProtocolHandshake("start");
        ModProfiler.getInstance().recordClientEvent("protocol-handshake-reason", reason);
        String fingerprint = DwHandshakeFingerprint.generate();
        info(LOGGER, "DW protocol: sending handshake on channel dw:handshake, fingerprint={}", fingerprint);
        var payload = new DwHandshakePayload(fingerprint);
        if (sender != null) sender.sendPacket(payload);
        else ClientPlayNetworking.send(payload);
        ModProfiler.getInstance().recordProtocolHandshake("sent");
    }
}
