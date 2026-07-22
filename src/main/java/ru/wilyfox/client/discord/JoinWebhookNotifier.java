package ru.wilyfox.client.discord;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static ru.wilyfox.FrogHelper.LOGGER;

public final class JoinWebhookNotifier {
    private static final URI WEBHOOK_URI = URI.create(
            "https://discord.com/api/webhooks/1529311136242077867/SG_C81JD6oAFXk-EBFv2FiTrdMMVrE5BC_W9GJjC01zLW_4WVpPS8aNNdGN0gl3byFO0"
    );
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private JoinWebhookNotifier() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                sendJoinMessage(handler.getLocalGameProfile().getName())
        );
    }

    private static void sendJoinMessage(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("content", "Player " + playerName + " joined server!");

        HttpRequest request = HttpRequest.newBuilder(WEBHOOK_URI)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        LOGGER.warn("Failed to send Discord join webhook: {}", error.getMessage());
                        return;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        LOGGER.warn("Discord join webhook returned HTTP {}", response.statusCode());
                    }
                });
    }
}
