package ru.wilyfox.client.protocol;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import ru.wilyfox.client.rune.RuneSetCooldownStore;
import ru.wilyfox.client.wand.WandCooldownTracker;

import java.util.Locale;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

final class ProtocolTransport {
    private static final long INITIAL_HANDSHAKE_INTERVAL_MS = 2_000L;
    private static final long HANDSHAKE_REFRESH_INTERVAL_MS = 120_000L;
    private static final long STALE_PROTOCOL_TIMEOUT_MS = 20_000L;
    private static final long STALE_HANDSHAKE_RETRY_INTERVAL_MS = 10_000L;

    private ProtocolTransport() {
    }

    static void init(ProtocolState state, ProtocolRouter router) {
        if (state.initialized) {
            return;
        }

        state.initialized = true;

        PayloadTypeRegistry.playC2S().register(DwHandshakePayload.TYPE, DwHandshakePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(DwEvoPlusPayload.TYPE, DwEvoPlusPayload.STREAM_CODEC);

        ClientPlayNetworking.registerGlobalReceiver(DwEvoPlusPayload.TYPE, (payload, context) -> {
            byte[] data = payload.data().clone();
            context.client().execute(() -> {
                state.receivedEvoPlusPayload = true;
                state.lastPayloadAt = System.currentTimeMillis();
                router.route(state, data);
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset(state));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isDiamondWorldConnection(client)) {
                return;
            }

            long now = System.currentTimeMillis();
            if (!shouldSendHandshake(state, now)) {
                return;
            }

            sendHandshake();
            state.lastHandshakeAt = now;
        });
    }

    private static void reset(ProtocolState state) {
        state.resetRuntimeState();

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
        if (state.potionStore != null) {
            state.potionStore.clear();
        }
        if (state.sellerCooldownStore != null) {
            state.sellerCooldownStore.clear();
        }
        if (state.comboProgressStore != null) {
            state.comboProgressStore.clear();
        }

        RuneSetCooldownStore.clear();
        WandCooldownTracker.getInstance().clear();
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

    private static void sendHandshake() {
        String fingerprint = DwHandshakeFingerprint.generate();
        info(LOGGER, "DW protocol: sending handshake on channel dw:handshake, fingerprint={}", fingerprint);
        ClientPlayNetworking.send(new DwHandshakePayload(fingerprint));
    }
}
