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
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded pages, shared session credentials, coalesced websocket notifications. Client-thread state. */
final class BackendChatClient {
    private final URI backend;
    private final HttpClient http;
    private final AtomicLong announced = new AtomicLong();
    private volatile long cursor;
    private final AtomicBoolean refreshRecent = new AtomicBoolean();
    private long oldest, resetRevision = -1, nextRead, nextSend, generation, lastRead, retryReadAt;
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
    void changed(long value) {
        if (value <= 0) throw new IllegalArgumentException();
        if (value <= cursor) refreshRecent.set(true);
        announced.accumulateAndGet(value, Math::max);
    }
    void reset() {
        generation++; enabled = false; resync = true;
        if (reading != null) reading.cancel(true); if (sending != null) sending.cancel(true);
        reading = sending = null; nextRead = nextSend = lastRead = retryReadAt = 0;
        refreshRecent.set(false);
    }
    void older() { if (enabled && olderAvailable && oldest > 0) olderRequested = true; }
    void tick(boolean ready, String bearer, String name) {
        enabled = ready;
        if (!ready || bearer == null) return;
        var store = ChatTabManager.getInstance();
        if (!java.util.Objects.equals(account, name)) { store.clearBackendMessages(); account = name; }
        if (resetRevision != store.backendResetRevision()) {
            resetRevision = store.backendResetRevision(); cursor = oldest = 0; initial = false;
            olderAvailable = olderRequested = false; announced.set(0); nextRead = retryReadAt = 0;
            refreshRecent.set(false);
        }
        long now = System.currentTimeMillis();
        boolean catchingUp = announced.get() > cursor;
        if (reading != null || now < lastRead + 600 || now < retryReadAt
                || now < nextRead && !catchingUp && !olderRequested && !refreshRecent.get()) return;
        if (initial && !catchingUp && !olderRequested && !resync && !refreshRecent.get() && !viewingFh()) return;
        boolean older = initial && !catchingUp && olderRequested;
        boolean recent = initial && !catchingUp && !older && !resync && refreshRecent.getAndSet(false);
        olderRequested = false;
        String query = "?media=1" + (older ? "&before=" + oldest : initial && cursor > 0 && !recent ? "&after=" + cursor : "");
        long attempt = generation;
        var request = HttpRequest.newBuilder(backend.resolve("/v1/chat" + query)).timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + bearer).GET().build();
        nextRead = now + 10_000;
        lastRead = now;
        reading = http.sendAsync(request, ignored -> new LimitedBodySubscriber(512 * 1024)).whenComplete((response, error) ->
                Minecraft.getInstance().execute(() -> {
                    if (attempt != generation) return;
                    reading = null;
                    try {
                        if (error != null || response.statusCode() != 200) {
                            if (recent) refreshRecent.set(true);
                            nextRead = retryReadAt = System.currentTimeMillis() + 10_000;
                            return;
                        }
                        var page = SocialWire.JSON.fromJson(new String(response.body(), StandardCharsets.UTF_8), Page.class);
                        page.validate(SocialWire.responseTime(response.headers(), System.currentTimeMillis()));
                        resync = false;
                        if (older && page.messages().stream().anyMatch(m -> m.id() >= oldest)
                                || !older && !recent && initial && page.messages().stream().anyMatch(m -> m.id() <= cursor)) throw new IllegalArgumentException();
                        retryReadAt = 0;
                        store.acceptBackendMessages(page.messages(), initial && !older);
                        for (var m : page.messages()) { cursor = Math.max(cursor, m.id()); oldest = oldest == 0 ? m.id() : Math.min(oldest, m.id()); }
                        if (older || !initial) olderAvailable = page.hasMore();
                        boolean moreNew = initial && !older && page.hasMore();
                        if (recent) moreNew = false;
                        // Check a bounded recent page after a tail read, to recover late Discord metadata.
                        if (initial && !older && !recent && !moreNew && viewingFh()) refreshRecent.set(true);
                        initial = true;
                        if (moreNew) announced.accumulateAndGet(cursor + 1, Math::max);
                        nextRead = System.currentTimeMillis() + (moreNew || announced.get() > cursor || !recent && refreshRecent.get() ? 600 : 10_000);
                    } catch (RuntimeException invalid) {
                        if (recent) refreshRecent.set(true);
                        ru.wilyfox.FrogHelper.LOGGER.warn("FH chat history rejected: {}", invalid.getMessage());
                        nextRead = retryReadAt = System.currentTimeMillis() + 10_000;
                    }
                }));
    }
    private static boolean viewingFh() {
        if (ChatTabManager.getInstance().getActiveTab() == ChatTab.FH) return true;
        String key = ru.wilyfox.client.hud.config.WidgetCatalog.CHAT_FH.key();
        var config = ru.wilyfox.client.hud.config.ConfigManager.get().chatWidgets.get(key);
        return config != null && config.detached && !config.deleted
                && ru.wilyfox.client.hud.config.ConfigManager.isWidgetInCurrentLayout(key);
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
    void sendImage(java.nio.file.Path path, String input, String bearer) {
        sendImage(() -> ru.wilyfox.client.chat.FhImages.read(path), input, bearer);
    }
    void sendImage(byte[] bytes, String input, String bearer) {
        if (bytes == null || bytes.length == 0 || bytes.length > ru.wilyfox.client.chat.FhImages.MAX_BYTES) {
            feedback("froghelper.chat.image_send_failed"); return;
        }
        byte[] snapshot = bytes.clone();
        sendImage(() -> { ru.wilyfox.client.chat.FhImages.dimensions(snapshot); return snapshot; }, input, bearer);
    }
    private void sendImage(java.util.concurrent.Callable<byte[]> source, String input, String bearer) {
        String text = input == null ? "" : input.strip();
        if (!enabled || bearer == null) { feedback("froghelper.chat.fh_unavailable"); return; }
        if (sending != null || System.currentTimeMillis() < nextSend) { feedback("froghelper.chat.fh_busy"); return; }
        if (!text.isEmpty()) {
            try { new BackendChatMessage(1, "Player", text, System.currentTimeMillis()).validate(); }
            catch (IllegalArgumentException invalid) { feedback("froghelper.chat.fh_invalid"); return; }
        }
        long attempt = generation;
        nextSend = System.currentTimeMillis() + 3100;
        String nonce = UUID.randomUUID().toString();
        sending = ru.wilyfox.client.chat.FhImages.supply(() -> {
            try { return source.call(); }
            catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
        }).thenCompose(bytes -> {
            if (attempt != generation) return CompletableFuture.<HttpResponse<byte[]>>failedFuture(new IllegalStateException("Session changed"));
            var request = HttpRequest.newBuilder(backend.resolve("/v1/chat/image?nonce=" + nonce + "&text="
                            + java.net.URLEncoder.encode(text, StandardCharsets.UTF_8))).timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + bearer).header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
            return http.sendAsync(request, ignored -> new LimitedBodySubscriber());
        }).whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
            if (attempt != generation) return;
            sending = null;
            try {
                if (error != null || response.statusCode() != 200) { feedback("froghelper.chat.image_send_failed"); return; }
                var message = SocialWire.JSON.fromJson(new String(response.body(), StandardCharsets.UTF_8), BackendChatMessage.class);
                message.validateAt(SocialWire.responseTime(response.headers(), System.currentTimeMillis()));
                ChatTabManager.getInstance().acceptBackendMessages(List.of(message), true); changed(message.id()); nextRead = 0;
                feedback("froghelper.chat.image_sent");
            } catch (RuntimeException invalid) { feedback("froghelper.chat.image_send_failed"); }
        }));
    }
    private static void feedback(String key) { Minecraft.getInstance().gui.hud.setOverlayMessage(Component.translatable(key), false); }
}
