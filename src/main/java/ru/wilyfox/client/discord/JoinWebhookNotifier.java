package ru.wilyfox.client.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static ru.wilyfox.FrogHelper.LOGGER;

public final class JoinWebhookNotifier {
    private static final URI WEBHOOK_URI = URI.create(
            "https://discord.com/api/webhooks/1529311136242077867/SG_C81JD6oAFXk-EBFv2FiTrdMMVrE5BC_W9GJjC01zLW_4WVpPS8aNNdGN0gl3byFO0"
    );
    private static final URI CREATE_MESSAGE_URI = URI.create(WEBHOOK_URI + "?wait=true");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "froghelper-join-webhook");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Object ACTIVE_SESSION_LOCK = new Object();

    private static Session activeSession;

    private JoinWebhookNotifier() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                onJoin(handler.getLocalGameProfile())
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
        ClientTickEvents.END_CLIENT_TICK.register(client -> pollLocation());
    }

    private static void onJoin(GameProfile profile) {
        if (profile == null || profile.getName() == null || profile.getName().isBlank()) {
            return;
        }

        Session session;
        synchronized (ACTIVE_SESSION_LOCK) {
            if (activeSession != null && activeSession.isOnlineFor(profile.getName())) {
                return;
            }

            session = new Session(profile.getName(), profile.getId(), Instant.now());
            activeSession = session;
        }

        EXECUTOR.execute(() -> createMessage(session));
    }

    private static void pollLocation() {
        Session session;
        synchronized (ACTIVE_SESSION_LOCK) {
            session = activeSession;
        }
        if (session == null) {
            return;
        }

        String locationId = DiamondWorldProtocolClient.getCurrentGameLocation();
        if (locationId == null || locationId.isBlank()) {
            return;
        }

        String displayName = DiamondWorldProtocolClient.getGameLocationDisplayName(locationId);
        if (displayName == null || displayName.isBlank()) {
            displayName = locationId;
        }

        if (session.updateLocation(locationId, displayName)) {
            EXECUTOR.execute(() -> updateMessage(session));
        }
    }

    private static void onDisconnect() {
        Session session;
        synchronized (ACTIVE_SESSION_LOCK) {
            session = activeSession;
            activeSession = null;
        }
        if (session != null && session.markLoggedOut(Instant.now())) {
            EXECUTOR.execute(() -> updateMessage(session));
        }
    }

    private static void createMessage(Session session) {
        SessionSnapshot snapshot = session.snapshot();
        HttpRequest request = jsonRequest(CREATE_MESSAGE_URI)
                .POST(HttpRequest.BodyPublishers.ofString(DiscordSessionEmbed.build(snapshot).toString()))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (!isSuccess(response.statusCode())) {
                LOGGER.warn("Discord join webhook returned HTTP {}", response.statusCode());
                return;
            }

            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            String messageId = body.has("id") ? body.get("id").getAsString() : "";
            if (messageId.isBlank()) {
                LOGGER.warn("Discord join webhook response did not contain a message id");
                return;
            }

            session.markCreated(messageId, snapshot.revision());
            updateMessage(session);
        } catch (Exception exception) {
            LOGGER.warn("Failed to create Discord join embed: {}", exception.getMessage());
        }
    }

    private static void updateMessage(Session session) {
        PendingUpdate pending = session.pendingUpdate();
        if (pending == null) {
            return;
        }

        URI editUri = URI.create(WEBHOOK_URI + "/messages/" + pending.messageId());
        HttpRequest request = jsonRequest(editUri)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(
                        DiscordSessionEmbed.build(pending.snapshot()).toString()
                ))
                .build();

        try {
            HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            if (!isSuccess(response.statusCode())) {
                LOGGER.warn("Discord join webhook edit returned HTTP {}", response.statusCode());
                return;
            }
            session.markUpdated(pending.snapshot().revision());
        } catch (Exception exception) {
            LOGGER.warn("Failed to update Discord join embed: {}", exception.getMessage());
        }
    }

    private static HttpRequest.Builder jsonRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static final class Session {
        private final String playerName;
        private final UUID playerId;
        private final Instant joinedAt;

        private String locationId = "";
        private String locationName = "Waiting for location";
        private Instant loggedOutAt;
        private long revision;
        private String messageId;
        private long sentRevision = -1L;

        private Session(String playerName, UUID playerId, Instant joinedAt) {
            this.playerName = playerName;
            this.playerId = playerId;
            this.joinedAt = joinedAt;
        }

        synchronized boolean isOnlineFor(String name) {
            return loggedOutAt == null && playerName.equalsIgnoreCase(name);
        }

        synchronized boolean updateLocation(String id, String name) {
            if (loggedOutAt != null
                    || Objects.equals(locationId, id) && Objects.equals(locationName, name)) {
                return false;
            }

            locationId = id;
            locationName = name;
            revision++;
            return true;
        }

        synchronized boolean markLoggedOut(Instant timestamp) {
            if (loggedOutAt != null) {
                return false;
            }

            loggedOutAt = timestamp;
            revision++;
            return true;
        }

        synchronized SessionSnapshot snapshot() {
            return new SessionSnapshot(
                    playerName,
                    playerId,
                    joinedAt,
                    locationName,
                    loggedOutAt,
                    revision
            );
        }

        synchronized void markCreated(String id, long revision) {
            messageId = id;
            sentRevision = revision;
        }

        synchronized PendingUpdate pendingUpdate() {
            if (messageId == null || messageId.isBlank() || revision <= sentRevision) {
                return null;
            }
            return new PendingUpdate(messageId, snapshot());
        }

        synchronized void markUpdated(long revision) {
            sentRevision = Math.max(sentRevision, revision);
        }
    }

    record SessionSnapshot(
            String playerName,
            UUID playerId,
            Instant joinedAt,
            String locationName,
            Instant loggedOutAt,
            long revision
    ) {
    }

    private record PendingUpdate(String messageId, SessionSnapshot snapshot) {
    }
}
