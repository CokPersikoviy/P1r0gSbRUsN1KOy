package ru.wilyfox.client.moduser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;
import ru.wilyfox.FrogHelper;
import ru.wilyfox.client.protocol.CurrentServerInfo;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.profiler.ModProfiler;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/** Automatic HTTPS login + WSS presence. All Minecraft state is touched on the client thread. */
public final class BackendSocialClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final AtomicReference<PendingRoster> rosterUpdates = new AtomicReference<>();
    private static boolean initialized;
    private volatile long generation;
    private String token;
    private String scope;
    private String rejectedToken;
    private String status = "social.froghelper.waiting";
    private WebSocket socket;
    private CompletableFuture<?> request;
    private long retryAt;
    private long requestStartedAt;
    private volatile long expiresAt;
    private volatile String accessToken;
    private long lastFrameAt;
    private int failures;
    private volatile int bufferedCharacters;
    private static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(FrogHelper.MOD_ID)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("Unknown");
    private SocialWire.SessionUpdate sentMetadata;
    private String sentLocationId;
    private CompletableFuture<WebSocket> metadataRequest;
    private long nextMetadataAt;
    private long nextMetadataSendAt;

    private static BackendSocialClient DEFAULT = new BackendSocialClient(SocialWire.BACKEND);
    private final URI backend;

    // Explicit dependencies let client game tests exercise the real login/reconnect
    // path against a local Go server without changing the production destination.
    BackendSocialClient(URI backend) {
        if (backend.getHost() == null || backend.getUserInfo() != null
                || backend.getQuery() != null || backend.getFragment() != null
                || !("https".equals(backend.getScheme())
                || "http".equals(backend.getScheme()) && "127.0.0.1".equals(backend.getHost()))) {
            throw new IllegalArgumentException("Backend must use HTTPS");
        }
        this.backend = backend;
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DEFAULT.reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> DEFAULT.tick(client));
    }

    public static String statusText() { return DEFAULT.status; }
    public static boolean diagnosticAuthorizationReady() {
        return DEFAULT.accessToken != null && DEFAULT.expiresAt > System.currentTimeMillis() + 10_000;
    }
    public record DiagnosticUploadResult(int status, long retryAfterMillis) {}
    public static CompletableFuture<DiagnosticUploadResult> uploadDiagnostic(Path archive, String digest) {
        var client = DEFAULT;
        String bearer = client.accessToken;
        if (bearer == null || client.expiresAt <= System.currentTimeMillis() + 5_000)
            return CompletableFuture.completedFuture(new DiagnosticUploadResult(401, 30_000));
        try {
            long attempt = client.generation;
            var request = HttpRequest.newBuilder(client.backend.resolve("/v1/diagnostics/" + digest))
                    .timeout(Duration.ofSeconds(95)).header("Content-Type", "application/zip")
                    .header("Authorization", "Bearer " + bearer).POST(HttpRequest.BodyPublishers.ofFile(archive)).build();
            return HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenApply(response -> {
                long retry = 30_000;
                try { retry = Math.max(retry, Math.min(3_600, Long.parseLong(response.headers().firstValue("Retry-After").orElse("0"))) * 1_000); }
                catch (NumberFormatException ignored) { }
                if (response.statusCode() == 401) Minecraft.getInstance().execute(() -> client.failed(attempt, false));
                return new DiagnosticUploadResult(response.statusCode(), retry);
            });
        } catch (IOException failure) { return CompletableFuture.failedFuture(new IOException("Cannot read diagnostic archive")); }
    }
    public static DebugSnapshot diagnosticSnapshot() {
        return new DebugSnapshot(DEFAULT.bufferedCharacters, DEFAULT.socket == null ? 0 : 1, DEFAULT.request == null ? 0 : 1);
    }
    public record DebugSnapshot(int bufferedCharacters, int connected, int requestPending) {}
    private record PendingRoster(long generation, List<String> names, long receivedAt) {}

    void tick(Minecraft client) {
        CurrentServerInfo server = DiamondWorldProtocolClient.getCurrentServerInfo();
        String nextToken = DiamondWorldProtocolClient.getGameToken();
        String nextScope = server.isKnown() ? server.family() + server.serverNumber() + ":" + server.mirror() : null;
        if (client.player == null || client.getConnection() == null
                || nextToken == null || nextToken.isBlank() || nextScope == null) {
            if (token != null || socket != null || request != null) reset();
            PresenceStore.clear();
            status = "social.froghelper.waiting";
            return;
        }
        if (!Objects.equals(token, nextToken) || !Objects.equals(scope, nextScope)) {
            reset();
            token = nextToken;
            scope = nextScope;
        }
        PendingRoster roster = rosterUpdates.getAndSet(null);
        if (roster != null && roster.generation() == generation) {
            PresenceStore.replace(roster.names());
            lastFrameAt = roster.receivedAt();
            failures = 0;
            status = roster.names().isEmpty() ? "social.froghelper.empty" : "social.froghelper.online";
        }
        long now = System.currentTimeMillis();
        if (socket != null && (now >= expiresAt - 20_000 || now - lastFrameAt > 90_000)) {
            failed(generation, false);
        }
        if (request != null && now - requestStartedAt > 15_000) failed(generation, false);
        if (socket != null) sendMetadata(now);
        if (now >= retryAt) rejectedToken = null;
        if (socket == null && request == null && now >= retryAt && !Objects.equals(token, rejectedToken)) {
            login(client.player.getGameProfile().name());
        }
    }

    void reset() {
        generation++;
        if (socket != null) socket.abort();
        if (request != null) request.cancel(true);
        socket = null;
        request = null;
        token = null;
        accessToken = null;
        expiresAt = 0;
        scope = null;
        rejectedToken = null;
        failures = 0;
        retryAt = 0;
        bufferedCharacters = 0;
        if (metadataRequest != null) metadataRequest.cancel(true);
        metadataRequest = null;
        sentMetadata = null;
        sentLocationId = null;
        nextMetadataAt = 0;
        nextMetadataSendAt = 0;
        rosterUpdates.set(null);
        PresenceStore.clear();
    }

    private void failed(long attempt, boolean rejected) {
        if (attempt != generation) return;
        String previousToken = token;
        String previousScope = scope;
        int previousFailures = failures;
        reset();
        token = previousToken;
        scope = previousScope;
        failures = Math.min(previousFailures + 1, 6);
        retryAt = System.currentTimeMillis() + Math.min(60_000L, 1_000L << failures)
                + ThreadLocalRandom.current().nextLong(1_000);
        rejectedToken = rejected ? token : null;
        if (rejected) retryAt = System.currentTimeMillis() + 120_000;
        status = rejected ? "social.froghelper.renewing" : "social.froghelper.unavailable";
    }

    private void login(String playerName) {
        long attempt = generation;
        String expectedScope = scope;
        status = "social.froghelper.connecting";
        HttpRequest login = HttpRequest.newBuilder(backend.resolve("/v1/session"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(SocialWire.JSON.toJson(new SocialWire.Login(token))))
                .build();
        requestStartedAt = System.currentTimeMillis();
        var exchange = HTTP.sendAsync(login, ignored -> new LimitedBodySubscriber());
        request = exchange;
        exchange.thenAccept(response -> {
            try {
                if (response.statusCode() != 200) {
                    Minecraft.getInstance().execute(() -> failed(attempt, response.statusCode() == 401));
                    return;
                }
                SocialWire.Session session = SocialWire.JSON.fromJson(new String(response.body(), StandardCharsets.UTF_8), SocialWire.Session.class);
                session.validate(playerName, System.currentTimeMillis() / 1_000);
                Minecraft.getInstance().execute(() -> {
                    if (attempt != generation) return;
                    expiresAt = session.expiresAt() * 1_000;
                    accessToken = session.accessToken();
                    URI uri = URI.create(("https".equals(backend.getScheme()) ? "wss://" : "ws://") + backend.getAuthority() + "/v1/presence?scope="
                            + URLEncoder.encode(expectedScope, StandardCharsets.UTF_8));
                    requestStartedAt = System.currentTimeMillis();
                    var opening = HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                            .header("Authorization", "Bearer " + session.accessToken())
                            .buildAsync(uri, new Listener(attempt, expectedScope));
                    request = opening;
                    opening.whenComplete((ws, failure) -> {
                        if (failure != null) Minecraft.getInstance().execute(() -> failed(attempt, false));
                    });
                });
            } catch (Exception ignored) {
                Minecraft.getInstance().execute(() -> failed(attempt, false));
            }
        }).exceptionally(failure -> {
            Minecraft.getInstance().execute(() -> failed(attempt, false));
            return null;
        });
    }

    private void sendMetadata(long now) {
        // The authenticated backend accepts at most one metadata frame per second.
        if (now < nextMetadataSendAt || metadataRequest != null && !metadataRequest.isDone()) return;
        String locationId = DiamondWorldProtocolClient.getCurrentGameLocation();
        boolean progressDue = now >= nextMetadataAt;
        if (!progressDue && sentMetadata != null && Objects.equals(locationId, sentLocationId)) return;
        if (progressDue) nextMetadataAt = now + 2_000;
        String location = locationId == null || locationId.isBlank() ? "Waiting for location"
                : DiamondWorldProtocolClient.getGameLocationDisplayName(locationId);
        if (location == null || location.isBlank()) location = locationId;
        if (location.codePointCount(0, location.length()) > 128) {
            location = location.substring(0, location.offsetByCodePoints(0, 128));
        }
        var metadata = new SocialWire.SessionUpdate(1, "session.update", MOD_VERSION,
                Math.clamp(DiamondWorldProtocolClient.getCurrentLevel(), 0, 1_000_000), location);
        if (metadata.equals(sentMetadata)) {
            sentLocationId = locationId;
            return;
        }
        // Transitions bypass the progress interval, within the backend's frame limit.
        boolean locationChanged = sentMetadata == null || !location.equals(sentMetadata.location());
        if (!locationChanged && !progressDue) {
            sentLocationId = locationId;
            return;
        }
        nextMetadataAt = now + 2_000;
        nextMetadataSendAt = now + 1_100;
        long attempt = generation;
        metadataRequest = socket.sendText(SocialWire.JSON.toJson(metadata), true);
        sentMetadata = metadata;
        sentLocationId = locationId;
        if (locationChanged) ModProfiler.getInstance().recordClientEvent("social-location-request", locationId);
        metadataRequest.whenComplete((ws, failure) -> {
            if (failure != null) Minecraft.getInstance().execute(() -> failed(attempt, false));
        });
    }

    private final class Listener implements WebSocket.Listener {
        private final long attempt;
        private final String expectedScope;
        private final StringBuilder buffer = new StringBuilder();
        private long sequence = -1;

        private Listener(long attempt, String expectedScope) {
            this.attempt = attempt;
            this.expectedScope = expectedScope;
        }

        @Override public void onOpen(WebSocket ws) {
            Minecraft.getInstance().execute(() -> {
                if (attempt != generation) { ws.abort(); return; }
                socket = ws;
                request = null;
                lastFrameAt = System.currentTimeMillis();
                status = "social.froghelper.connecting";
            });
            ws.request(1);
        }

        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            if (attempt != generation) { ws.abort(); return CompletableFuture.completedFuture(null); }
            try {
                if (buffer.length() + data.length() > SocialWire.MAX_MESSAGE_SIZE) throw new IllegalArgumentException();
                buffer.append(data);
                bufferedCharacters = buffer.length();
                if (last) {
                    SocialWire.Snapshot message = SocialWire.JSON.fromJson(buffer.toString(), SocialWire.Snapshot.class);
                    List<String> names = message.validatedNames(expectedScope);
                    if (message.sequence() <= sequence) throw new IllegalArgumentException();
                    sequence = message.sequence();
                    PendingRoster next = new PendingRoster(attempt, names, System.currentTimeMillis());
                    rosterUpdates.accumulateAndGet(next, (previous, incoming) -> previous != null
                            && previous.generation() > incoming.generation() ? previous : incoming);
                    buffer.setLength(0);
                    bufferedCharacters = 0;
                }
                ws.request(1);
            } catch (Exception ignored) {
                ws.abort();
                Minecraft.getInstance().execute(() -> failed(attempt, false));
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<?> onBinary(WebSocket ws, java.nio.ByteBuffer data, boolean last) {
            ws.abort();
            Minecraft.getInstance().execute(() -> failed(attempt, false));
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<?> onPing(WebSocket ws, java.nio.ByteBuffer data) {
            Minecraft.getInstance().execute(() -> {
                if (attempt == generation) lastFrameAt = System.currentTimeMillis();
            });
            return WebSocket.Listener.super.onPing(ws, data);
        }

        @Override public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            Minecraft.getInstance().execute(() -> failed(attempt, false));
            return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket ws, Throwable error) {
            Minecraft.getInstance().execute(() -> failed(attempt, false));
        }
    }
}
