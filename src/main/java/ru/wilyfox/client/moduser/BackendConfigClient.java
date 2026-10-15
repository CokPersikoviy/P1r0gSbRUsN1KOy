package ru.wilyfox.client.moduser;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.ConfigProfiles;
import ru.wilyfox.client.hud.config.SharedConfigCodec;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Main-profile publication and bounded downloads use the existing authenticated backend session. */
final class BackendConfigClient {
    private final URI backend;
    private final HttpClient http;
    private final Runnable unauthorized;
    private CompletableFuture<?> upload, download;
    private CompletableFuture<Component> result;
    private long generation, nextPublish, lastPublished, nextDownload;
    private String sent;
    BackendConfigClient(URI backend, HttpClient http, Runnable unauthorized) {
        this.backend = backend; this.http = http; this.unauthorized = unauthorized;
    }
    void reset() {
        generation++; sent = null; nextPublish = lastPublished = nextDownload = 0;
        if (upload != null) upload.cancel(true); if (download != null) download.cancel(true);
        if (result != null) result.complete(Component.translatable("social.froghelper.config.cancelled"));
        upload = download = null; result = null;
    }
    void tick(boolean ready, String token) {
        long now = System.currentTimeMillis();
        if (!ready || token == null || now < nextPublish || upload != null && !upload.isDone()) return;
        nextPublish = now + 5_000;
        final String body;
        try (var ignored = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("config/share/prepare")) {
            body = ConfigProfiles.primarySharedEncoded();
        }
        catch (Exception failure) { nextPublish = now + 60_000; return; }
        if (body.equals(sent) && now - lastPublished < 30_000) return;
        long attempt = generation;
        var request = HttpRequest.newBuilder(backend.resolve("/v1/config")).timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        upload = http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, failure) ->
                Minecraft.getInstance().execute(() -> {
                    if (attempt != generation) return;
                    if (failure == null && response.statusCode() == 204) { sent = body; lastPublished = now; }
                    else if (failure == null && response.statusCode() == 401) unauthorized.run();
                    else if (failure == null && response.statusCode() == 404) nextPublish = System.currentTimeMillis() + 60_000;
                }));
    }
    CompletableFuture<Component> download(SocialWire.Player player, String token) {
        long now = System.currentTimeMillis();
        if (now < nextDownload || download != null && !download.isDone())
            return CompletableFuture.completedFuture(Component.translatable("social.froghelper.config.busy"));
        nextDownload = now + 1_100;
        long attempt = generation;
        var completion = new CompletableFuture<Component>(); result = completion;
        var request = HttpRequest.newBuilder(backend.resolve("/v1/config/" + player.id()))
                .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token).GET().build();
        download = http.sendAsync(request, ignored -> new LimitedBodySubscriber(SharedConfigCodec.MAX_BYTES + 512))
                .whenComplete((response, failure) -> Minecraft.getInstance().execute(() -> {
                    if (attempt != generation) { completion.complete(Component.translatable("social.froghelper.config.cancelled")); return; }
                    try {
                        if (failure != null || response.statusCode() != 200) {
                            if (failure == null && response.statusCode() == 401) unauthorized.run();
                            completion.complete(Component.translatable("social.froghelper.config.failed")); return;
                        }
                        var envelope = JsonParser.parseString(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                        if (envelope.get("version").getAsInt() != 1 || !player.id().equals(envelope.get("id").getAsString())
                                || !player.name().equalsIgnoreCase(envelope.get("name").getAsString())) throw new IllegalArgumentException("Wrong config author");
                        ConfigProfiles.download(player.name(), SharedConfigCodec.normalize(envelope.get("config")));
                        completion.complete(Component.translatable("social.froghelper.config.downloaded", player.name()));
                    } catch (Exception invalid) { completion.complete(Component.translatable("social.froghelper.config.failed")); }
                }));
        return completion;
    }
}
