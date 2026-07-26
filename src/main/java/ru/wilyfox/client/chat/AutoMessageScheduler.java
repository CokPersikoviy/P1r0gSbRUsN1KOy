package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import ru.wilyfox.client.hud.config.AutoMessageEntryConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.util.ArrayList;
import java.util.List;

public final class AutoMessageScheduler {
    private static final AutoMessageScheduler INSTANCE = new AutoMessageScheduler();

    private final List<SlotState> slotStates = new ArrayList<>();
    private final MarketAutoMessageState marketState = new MarketAutoMessageState();
    private boolean initialized;

    private AutoMessageScheduler() {
    }

    public static AutoMessageScheduler getInstance() {
        return INSTANCE;
    }

    public void register() {
        if (initialized) {
            return;
        }

        initialized = true;

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("tick/AutoMessageScheduler")) {
                if (client.player == null || client.player.connection == null) {
                    return;
                }

                tick();
            }
        });
    }

    private void tick() {
        ensureSlotCount(ConfigManager.get().autoMessages.entries.size());
        long now = System.currentTimeMillis();

        if (!ConfigManager.get().autoMessages.active) {
            for (SlotState state : slotStates) {
                state.reset();
            }
            marketState.reset();
            return;
        }

        List<String> marketMessages = new ArrayList<>();
        for (int index = 0; index < ConfigManager.get().autoMessages.entries.size(); index++) {
            AutoMessageEntryConfig entry = ConfigManager.get().autoMessages.entries.get(index);
            SlotState state = slotStates.get(index);
            String message = entry.message == null ? "" : entry.message.trim();
            int delaySeconds = Math.max(1, entry.delaySeconds);
            String fingerprint = entry.active + "|" + entry.useMarketCooldown + "|" + delaySeconds + "|" + message;

            if (!entry.active || message.isBlank()) {
                state.reset();
                continue;
            }

            if (entry.useMarketCooldown) {
                state.reset();
                marketMessages.add(message);
                continue;
            }

            if (!fingerprint.equals(state.fingerprint)) {
                state.fingerprint = fingerprint;
                state.nextSendAt = now + delaySeconds * 1000L;
            }

            if (state.nextSendAt <= 0L) {
                state.nextSendAt = now + delaySeconds * 1000L;
                continue;
            }

            if (now < state.nextSendAt) {
                continue;
            }

            if (!ChatDispatchQueue.containsQueuedChat(message)) {
                ChatDispatchQueue.enqueueChat(message, 0L);
            }

            state.nextSendAt = now + delaySeconds * 1000L;
        }

        processMarketMessage(marketMessages, now);
    }

    private void processMarketMessage(List<String> messages, long now) {
        if (messages.isEmpty()) {
            marketState.reset();
            return;
        }

        long cooldownRevision = DiamondWorldProtocolClient.getMarketCooldownRevision();
        String message = marketState.nextMessage(
                messages,
                DiamondWorldProtocolClient.getMarketCooldownEndsAt(),
                cooldownRevision,
                now
        );
        if (message == null || ChatDispatchQueue.containsQueuedChat(message)) {
            return;
        }

        ChatDispatchQueue.enqueueChat(message, 0L);
        marketState.markQueued(cooldownRevision);
    }

    private void ensureSlotCount(int count) {
        while (slotStates.size() < count) {
            slotStates.add(new SlotState());
        }
        while (slotStates.size() > count) {
            slotStates.removeLast();
        }
    }

    private void reset() {
        for (SlotState state : slotStates) {
            state.reset();
        }
        marketState.reset();
    }

    private static final class SlotState {
        private String fingerprint = "";
        private long nextSendAt = 0L;

        private void reset() {
            fingerprint = "";
            nextSendAt = 0L;
        }
    }
}
