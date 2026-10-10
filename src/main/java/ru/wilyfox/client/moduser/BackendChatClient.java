package ru.wilyfox.client.moduser;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.chat.BackendChatMessage;
import ru.wilyfox.client.chat.ChatTab;
import ru.wilyfox.client.chat.ChatTabManager;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded pages, shared session credentials, coalesced websocket notifications. Client-thread state. */
final class BackendChatClient {
    private final URI backend;
    private final HttpClient http;
    private final AtomicLong announced = new AtomicLong();
    private long cursor, oldest, resetRevision = -1, nextRead, nextSend, generation, lastRead;
    private boolean initial, olderAvailable, olderRequested, enabled, resync = true;
    private String account;
    private CompletableFuture<?> reading, sending;
    BackendChatClient(URI backend, HttpClient http) { this.backend = backend; this.http = http; }
    record Page(int version, List<BackendChatMessage> messages, boolean hasMore) {
        void validate(long backendNow) {
            if (version != 1 || messages == null || messages.size() > 32) throw new IllegalArgumentException("Invalid chat page");
            long previous = 0;
            for (var m : messages) { if (m == null) throw new IllegalArgumentException(); m.validateAt(backendNow);
                if (m.id() <= previous) throw new IllegalArgumentException("Unordered chat page"); previous = m.id(); }
        }
    }
    void changed(long value) { if (value <= 0) throw new IllegalArgumentException(); announced.accumulateAndGet(value, Math::max); }
    void reset() {
        generation++; enabled = false; resync = true;
        if (reading != null) reading.cancel(true); if (sending != null) sending.cancel(true);
        reading = sending = null; nextRead = nextSend = lastRead = 0;
    }
    void older() { if (enabled && olderAvailable && oldest > 0) olderRequested = true; }
    void tick(boolean ready, String bearer, String name) {
        enabled = ready;
        if (!ready || bearer == null) return;
        var store = ChatTabManager.getInstance();
        if (!java.util.Objects.equals(account, name)) { store.clearBackendMessages(); account = name; }
        if (resetRevision != store.backendResetRevision()) {
            resetRevision = store.backendResetRevision(); cursor = oldest = 0; initial = false;
            olderAvailable = olderRequested = false; announced.set(0); nextRead = 0;
        }
        long now = System.currentTimeMillis();
        boolean catchingUp = announced.get() > cursor;
        if (reading != null || now < lastRead + 600 || now < nextRead && !catchingUp && !olderRequested) return;
        if (initial && !catchingUp && !olderRequested && !resync && store.getActiveTab() != ChatTab.FH) return;
        boolean older = initial && !catchingUp && olderRequested;
        olderRequested = false;
        String query = older ? "?before=" + oldest : initial && cursor > 0 ? "?after=" + cursor : "";
        long attempt = generation;
        var request = HttpRequest.newBuilder(backend.resolve("/v1/chat" + query)).timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + bearer).GET().build();
        nextRead = now + 10_000;
        lastRead = now;
        reading = http.sendAsync(request, ignored -> new LimitedBodySubscriber()).whenComplete((response, error) ->
                Minecraft.getInstance().execute(() -> {
                    if (attempt != generation) return;
                    reading = null;
                    try {
                        if (error != null || response.statusCode() != 200) { nextRead = System.currentTimeMillis() + 10_000; return; }
                        var page = SocialWire.JSON.fromJson(new String(response.body(), StandardCharsets.UTF_8), Page.class);
                        page.validate(SocialWire.responseTime(response.headers(), System.currentTimeMillis()));
                        resync = false;
                        if (older && page.messages().stream().anyMatch(m -> m.id() >= oldest)
                                || !older && initial && page.messages().stream().anyMatch(m -> m.id() <= cursor)) throw new IllegalArgumentException();
                        store.acceptBackendMessages(page.messages(), initial && !older);
                        for (var m : page.messages()) { cursor = Math.max(cursor, m.id()); oldest = oldest == 0 ? m.id() : Math.min(oldest, m.id()); }
                        if (older || !initial) olderAvailable = page.hasMore();
                        boolean moreNew = initial && !older && page.hasMore();
                        initial = true;
                        if (moreNew) announced.accumulateAndGet(cursor + 1, Math::max);
                        nextRead = System.currentTimeMillis() + (moreNew || announced.get() > cursor ? 600 : 10_000);
                    } catch (RuntimeException invalid) {
                        ru.wilyfox.FrogHelper.LOGGER.warn("FH chat history rejected: {}", invalid.getMessage());
                        nextRead = System.currentTimeMillis() + 10_000;
                    }
                }));
    }
    void send(String input, String bearer) {
        String text = input == null ? "" : input.strip();
        if (text.isEmpty()) return;
        if (!enabled || bearer == null) { feedback("froghelper.chat.fh_unavailable"); return; }
        if (sending != null || System.currentTimeMillis() < nextSend) { feedback("froghelper.chat.fh_busy"); return; }
        try { new BackendChatMessage(1, "Player", text, System.currentTimeMillis()).validate(); }
        catch (IllegalArgumentException invalid) { feedback("froghelper.chat.fh_invalid"); return; }
        long attempt = generation;
        nextSend = System.currentTimeMillis() + 3100;
        var body = java.util.Map.of("nonce", UUID.randomUUID().toString(), "text", text);
        var request = HttpRequest.newBuilder(backend.resolve("/v1/chat")).timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + bearer).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(SocialWire.JSON.toJson(body))).build();
        sending = http.sendAsync(request, ignored -> new LimitedBodySubscriber()).whenComplete((response, error) ->
                Minecraft.getInstance().execute(() -> {
                    if (attempt != generation) return;
                    sending = null;
                    try {
                        if (error != null || response.statusCode() != 200) {
                            ru.wilyfox.FrogHelper.LOGGER.warn("FH message confirmation failed: {}", error != null
                                    ? error.getClass().getSimpleName() : "HTTP " + response.statusCode());
                            feedback("froghelper.chat.fh_failed"); return;
                        }
                        var message = SocialWire.JSON.fromJson(new String(response.body(), StandardCharsets.UTF_8), BackendChatMessage.class);
                        message.validateAt(SocialWire.responseTime(response.headers(), System.currentTimeMillis()));
                        ChatTabManager.getInstance().acceptBackendMessages(List.of(message), true);
                        changed(message.id()); nextRead = 0;
                    } catch (RuntimeException invalid) {
                        ru.wilyfox.FrogHelper.LOGGER.warn("FH message confirmation rejected: {}", invalid.getMessage());
                        feedback("froghelper.chat.fh_failed");
                    }
                }));
    }
    private static void feedback(String key) { Minecraft.getInstance().gui.hud.setOverlayMessage(Component.translatable(key), false); }
}
