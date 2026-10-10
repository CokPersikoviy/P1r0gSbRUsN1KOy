package ru.wilyfox.client.moduser;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.RenderConfig;
import ru.wilyfox.client.keybinds.KeyBinds;
import ru.wilyfox.client.protocol.SocialProtocolFixture;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class SocialBackendClientTest implements FabricClientGameTest {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Override public void runTest(ClientGameTestContext context) {
        testKeybind(context);
        String fixtureFile = System.getenv("FH_CLIENT_FIXTURE_FILE");
        if (fixtureFile == null || fixtureFile.isBlank()) return;
        try {
            testBackend(context, URI.create(Files.readString(Path.of(fixtureFile)).trim()));
        } catch (Exception exception) {
            throw new AssertionError("Client/backend integration failed", exception);
        }
    }

    private static void testKeybind(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            AtomicReference<InputConstants.Key> originalKey = new AtomicReference<>();
            context.runOnClient(client -> {
                originalKey.set(KeyMappingHelper.getBoundKeyOf(KeyBinds.SOCIAL));
                KeyBinds.SOCIAL.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));
                KeyMapping.resetMapping();
                KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));
            });
            try {
                context.waitTicks(2);
                context.runOnClient(client -> {
                    if (!(client.gui.screen() instanceof SocialScreen screen) || screen.isPauseScreen()) {
                        throw new AssertionError("Remapped online key must open a non-pausing screen");
                    }
                    if (screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_C, 0, 0)) || client.gui.screen() != screen) {
                        throw new AssertionError("Previous default key must not close a remapped screen");
                    }
                    if (!screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_G, 0, 0)) || client.gui.screen() != null) {
                        throw new AssertionError("Remapped online key must close the screen");
                    }
                });
            } finally {
                context.runOnClient(client -> {
                    KeyBinds.SOCIAL.setKey(originalKey.get());
                    KeyMapping.resetMapping();
                    client.gui.setScreen(null);
                });
            }
        }
    }

    private static void testBackend(ClientGameTestContext context, URI backend) throws Exception {
        if (!"http".equals(backend.getScheme()) || !"127.0.0.1".equals(backend.getHost())) {
            throw new AssertionError("The integration fixture must be loopback only");
        }
        Field defaultClient = BackendSocialClient.class.getDeclaredField("DEFAULT");
        defaultClient.setAccessible(true);
        BackendSocialClient original = (BackendSocialClient) defaultClient.get(null);
        BackendSocialClient testClient = new BackendSocialClient(backend);
        AtomicReference<RenderConfig> originalRender = new AtomicReference<>();
        context.runOnClient(client -> {
            originalRender.set(ConfigManager.get().render);
            ConfigManager.get().render = new Gson().fromJson(
                    "{\"socialsEnabled\":false,\"modUserMesh\":false}", RenderConfig.class);
            original.reset();
            try { defaultClient.set(null, testClient); }
            catch (IllegalAccessException exception) { throw new AssertionError(exception); }
        });
        try (var world = context.worldBuilder().create()) {
            AtomicReference<String> name = new AtomicReference<>();
            context.runOnClient(client -> name.set(client.player.getGameProfile().name()));
            String token = get(backend, "/fixture/token?name=" + URLEncoder.encode(name.get(), StandardCharsets.UTF_8))
                    .get("gameToken").getAsString();
            context.waitTicks(3);
            if (get(backend, "/fixture/status").get("creates").getAsInt() != 0) {
                throw new AssertionError("No embed is allowed before authenticated DW login");
            }
            context.runOnClient(client -> {
                SocialProtocolFixture.location("HUB", 0, "spawn_overworld");
                SocialProtocolFixture.token(token);
            });
            JsonObject joined = await(context, backend, "/fixture/status", data ->
                    data.get("creates").getAsInt() == 1 && field(data, "Location").equals("Спавн")
                            && field(data, "Online").equals("Yes"));
            String version = FabricLoader.getInstance().getModContainer("froghelper").orElseThrow()
                    .getMetadata().getVersion().getFriendlyString();
            if (!field(joined, "Nickname").startsWith(name.get() + " [") || !field(joined, "Version").equals(version)) {
                throw new AssertionError("Authenticated name and mod version must reach the server embed");
            }
            // Webhook delivery and the client's WebSocket roster arrive independently.
            for (int i = 0; i < 100 && !context.computeOnClient(client -> PresenceStore.isKnown(name.get())); i++)
                context.waitTicks(2);
            context.runOnClient(client -> {
                if (!PresenceStore.isKnown(name.get())) throw new AssertionError("Self missing from authenticated roster");
                KeyMapping.click(KeyMappingHelper.getBoundKeyOf(KeyBinds.SOCIAL));
            });
            context.waitTicks(2);
            context.takeScreenshot("social-backend-online");
            context.runOnClient(client -> {
                if (!(client.gui.screen() instanceof SocialScreen)) throw new AssertionError("Online bind did not open");
                client.gui.setScreen(null);
                SocialProtocolFixture.location("PRISONEVO1", 2, "market");
            });
            JsonObject moved = await(context, backend, "/fixture/status", data ->
                    field(data, "Location").equals("Рынок") && data.get("edits").getAsInt() >= 1);
            if (moved.get("creates").getAsInt() != 1 || !field(moved, "Timestamp").equals(field(joined, "Timestamp"))) {
                throw new AssertionError("Mirror transitions must update the same embed and preserve login time");
            }
            testBackendChat(context, backend, name.get());
            testTimerButton(context, backend);
            // Exercise the real bearer/file upload path, then retry the identical ZIP.
            Path diagnostic = Files.createTempFile("fh-diagnostic-integration-",".zip");
            try {
                try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(diagnostic))) {
                    String manifest = new Gson().toJson(java.util.Map.of("version",1,"kind","crash","modVersion",version,
                            "minecraftVersion","26.2","createdAtMs",System.currentTimeMillis(),"source","minecraft","sourceHash","a".repeat(64)));
                    zip.putNextEntry(new java.util.zip.ZipEntry("manifest.json")); zip.write(manifest.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                    zip.putNextEntry(new java.util.zip.ZipEntry("crash.txt")); zip.write("Integration crash stack".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                }
                String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(diagnostic)));
                var first = BackendSocialClient.uploadDiagnostic(diagnostic,digest).get(10,java.util.concurrent.TimeUnit.SECONDS);
                var repeated = BackendSocialClient.uploadDiagnostic(diagnostic,digest).get(10,java.util.concurrent.TimeUnit.SECONDS);
                if (first.status()!=202 || repeated.status()!=200) throw new AssertionError("Diagnostic acceptance/deduplication failed");
                JsonObject delivered = await(context,backend,"/fixture/status",data -> data.get("diagnosticAttachments").getAsInt()==1);
                if (delivered.get("diagnosticCreates").getAsInt()!=1 || !delivered.get("diagnosticDigest").getAsString().equals(digest))
                    throw new AssertionError("Diagnostic webhook did not receive the exact archive once");
            } finally { Files.deleteIfExists(diagnostic); }
            context.runOnClient(client -> SocialProtocolFixture.token(null));
            await(context, backend, "/fixture/status?logout=1", data -> !field(data, "Logout timestamp").isEmpty());
            context.runOnClient(client -> {
                if (PresenceStore.knownCount() != 0) throw new AssertionError("Logout retained online badges");
            });
        } finally {
            context.runOnClient(client -> {
                testClient.reset();
                SocialProtocolFixture.clear();
                ConfigManager.get().render = originalRender.get();
                try { defaultClient.set(null, original); }
                catch (IllegalAccessException exception) { throw new AssertionError(exception); }
            });
            HTTP.send(HttpRequest.newBuilder(backend.resolve("/fixture/stop")).timeout(Duration.ofSeconds(3))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
        }
    }

    private static void testBackendChat(ClientGameTestContext context, URI backend, String name) throws Exception {
        var tabs = ru.wilyfox.client.chat.ChatTabManager.getInstance();
        var profiler = ru.wilyfox.client.profiler.ModProfiler.getInstance();
        boolean wasEnabled = profiler.isEnabled();
        for (int i = 0; i < 100 && context.computeOnClient(client -> tabs.getMessages(ru.wilyfox.client.chat.ChatTab.FH).size()) < 32; i++) context.waitTicks(2);
        context.runOnClient(client -> {
            if (tabs.getMessages(ru.wilyfox.client.chat.ChatTab.FH).size() != 32) throw new AssertionError("Initial FH history page missing");
            ru.wilyfox.client.chat.ChatDock.select(ru.wilyfox.client.chat.ChatDock.key(ru.wilyfox.client.chat.ChatTab.FH));
            if (((ru.wilyfox.bridge.ChatComponentAccessor) client.gui.hud.getChat()).froghelper$getTrimmedMessages().isEmpty())
                throw new AssertionError("Backend history not displayed in native FH tab");
            profiler.start();
        });
        long before = context.computeOnClient(client -> chatPackets(profiler));
        try {
            context.setScreen(() -> new net.minecraft.client.gui.screens.ChatScreen("/msg literal backend text", false));
            context.waitTicks(3); context.takeScreenshot("fh-chat-history");
            context.runOnClient(client -> {
                var screen = (net.minecraft.client.gui.screens.ChatScreen) client.gui.screen();
                if (!screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)) || client.gui.screen() != null)
                    throw new AssertionError("FH Enter was blocked before backend submission");
            });
            for (int i = 0; i < 100 && context.computeOnClient(client -> tabs.getMessages(ru.wilyfox.client.chat.ChatTab.FH).size()) < 33; i++) context.waitTicks(2);
            context.runOnClient(client -> {
                var messages = tabs.getMessages(ru.wilyfox.client.chat.ChatTab.FH);
                if (messages.size() != 33 || !messages.getLast().component().getString().contains(name + ": /msg literal backend text"))
                    throw new AssertionError("FH send did not reach backend with authenticated identity");
                if (chatPackets(profiler) != before) throw new AssertionError("FH input sent a Minecraft chat/command/completion packet");
                if (((ru.wilyfox.bridge.ChatComponentAccessor) client.gui.hud.getChat()).froghelper$getAllMessages().stream()
                        .anyMatch(m -> m.content().getString().contains("literal backend text"))) throw new AssertionError("Backend message leaked into native game archive");
            });
            HTTP.send(HttpRequest.newBuilder(backend.resolve("/fixture/chat-message")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"Remote FH message\"}")).build(), HttpResponse.BodyHandlers.discarding());
            for (int i = 0; i < 100 && context.computeOnClient(client -> tabs.getMessages(ru.wilyfox.client.chat.ChatTab.FH).size()) < 34; i++) context.waitTicks(2);
            context.runOnClient(client -> {
                if (tabs.getMessages(ru.wilyfox.client.chat.ChatTab.FH).size() != 34) throw new AssertionError("Websocket FH notification missed remote message");
                client.gui.hud.getChat().scrollChat(10000);
            });
            for (int i = 0; i < 100 && context.computeOnClient(client -> tabs.getMessages(ru.wilyfox.client.chat.ChatTab.FH).size()) < 66; i++) context.waitTicks(2);
            context.runOnClient(client -> {
                if (tabs.getMessages(ru.wilyfox.client.chat.ChatTab.FH).size() != 66) throw new AssertionError("Scrolling FH did not load older history");
                client.gui.hud.getChat().rescaleChat();
                if (tabs.getMessages(ru.wilyfox.client.chat.ChatTab.FH).size() != 66) throw new AssertionError("Rescaling lost FH history");
                tabs.setActiveTab(ru.wilyfox.client.chat.ChatTab.ALL);
                if (tabs.getMessages(ru.wilyfox.client.chat.ChatTab.ALL).stream().anyMatch(m -> m.component().getString().contains("FH history")
                        || m.component().getString().contains("literal backend text"))) throw new AssertionError("FH history leaked into ALL");
            });
            context.takeScreenshot("fh-chat-pinned-tabs");
        } finally {
            context.runOnClient(client -> { client.gui.setScreen(null); tabs.setActiveTab(ru.wilyfox.client.chat.ChatTab.ALL); if (!wasEnabled) profiler.stop(); });
        }
    }
    private static long chatPackets(ru.wilyfox.client.profiler.ModProfiler profiler) {
        try {
            var method = profiler.getClass().getDeclaredMethod("snapshotLocked", Class.forName("ru.wilyfox.client.profiler.ProfilerDiagnostics$FullDiagnostics"));
            method.setAccessible(true);
            synchronized (profiler) {
                var snapshot = (ru.wilyfox.client.profiler.ModProfiler.ReportSnapshot) method.invoke(profiler, new Object[]{null});
                return snapshot.counters().stream().filter(c -> c.name().startsWith("network/serverbound/type/")
                        && (c.name().contains("Chat") || c.name().contains("CommandSuggestion"))).mapToLong(ru.wilyfox.client.profiler.ModProfiler.CounterView::total).sum();
            }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void testTimerButton(ClientGameTestContext context, URI backend) throws Exception {
        HTTP.send(HttpRequest.newBuilder(backend.resolve("/fixture/timer-player")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding());
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        for (int i = 0; i < 100 && !ready.get(); i++) {
            context.waitTicks(2);
            context.runOnClient(client -> ready.set(PresenceStore.player("TimerSource") != null));
        }
        if (!ready.get()) throw new AssertionError("Extended timer roster never arrived");
        var repoField = SocialTimerService.class.getDeclaredField("repository"); repoField.setAccessible(true);
        var originalRepo = (ru.wilyfox.boss.BossRepository) repoField.get(null);
        var isolated = new ru.wilyfox.boss.BossRepository();
        var oldMode = ConfigManager.get().bossWidget.sourceMode;
        try {
            context.runOnClient(client -> {
                SocialTimerService.bindRepository(isolated);
                ConfigManager.get().bossWidget.sourceMode = ru.wilyfox.client.hud.config.BossTimerSourceMode.PROTOCOL_ONLY;
                var source = PresenceStore.player("TimerSource");
                if (source.timerCount() != 61 || !source.protocolBadge() || BackendSocialClient.timerUnavailable(source) != null)
                    throw new AssertionError("Protocol badge/count or cross-subserver request missing");
                if (!SocialScreen.playerLabel("TimerSource").getString().contains("P TimerSource [61]"))
                    throw new AssertionError("Timer label has the wrong format");
                client.gui.setScreen(new SocialScreen());
            });
            context.waitTicks(2);
            context.takeScreenshot("social-timer-request");
            var response = new AtomicReference<java.util.concurrent.CompletableFuture<Component>>();
            context.runOnClient(client -> {
                var screen = (SocialScreen) client.gui.screen();
                try {
                    var x = SocialScreen.class.getDeclaredField("panelX"); x.setAccessible(true);
                    var y = SocialScreen.class.getDeclaredField("panelY"); y.setAccessible(true);
                    int row = PresenceStore.knownDisplayNames().indexOf("TimerSource");
                    var event = new net.minecraft.client.input.MouseButtonEvent(x.getInt(screen) + 300, y.getInt(screen) + 40 + row * 16,
                            new net.minecraft.client.input.MouseButtonInfo(0, 0));
                    if (!screen.mouseClicked(event, false)) throw new AssertionError("Timer button click was not handled");
                    var pending = SocialScreen.class.getDeclaredField("pending"); pending.setAccessible(true);
                    response.set((java.util.concurrent.CompletableFuture<Component>) pending.get(screen));
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            for (int i = 0; i < 100 && !response.get().isDone(); i++) context.waitTicks(2);
            if (!response.get().isDone()) throw new AssertionError("Timer request never completed");
            context.runOnClient(client -> {
                if (isolated.getAllProtocol().size() != 61 || isolated.getAllMerged().size() != 61)
                    throw new AssertionError("Imported timers are hidden in Protocol Only");
                if (!response.get().join().getString().contains("61")) throw new AssertionError("Missing import feedback");
                long remaining = isolated.getAllProtocol().iterator().next().getRespawnAt() - System.currentTimeMillis();
                if (remaining < 100_000 || remaining > 122_000)
                    throw new AssertionError("Backend clock skew changed the remaining timer duration: " + remaining);
            });
            context.takeScreenshot("social-timers-received");
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                SocialTimerService.bindRepository(originalRepo);
                ConfigManager.get().bossWidget.sourceMode = oldMode;
            });
        }
    }

    private static JsonObject await(ClientGameTestContext context, URI backend, String path,
                                    Predicate<JsonObject> condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            context.waitTicks(2);
            JsonObject data = get(backend, path);
            if (condition.test(data)) return data;
        }
        throw new AssertionError("Timed out waiting for client presence/embed");
    }

    private static JsonObject get(URI backend, String path) throws Exception {
        var response = HTTP.send(HttpRequest.newBuilder(backend.resolve(path)).timeout(Duration.ofSeconds(3)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new AssertionError("Fixture HTTP " + response.statusCode());
        return SocialWire.JSON.fromJson(response.body(), JsonObject.class);
    }

    private static String field(JsonObject status, String name) {
        JsonObject payload = status.getAsJsonObject("payload");
        if (payload == null || !payload.has("embeds") || payload.get("embeds").isJsonNull()) return "";
        for (var element : payload.getAsJsonArray("embeds").get(0).getAsJsonObject().getAsJsonArray("fields")) {
            JsonObject value = element.getAsJsonObject();
            if (value.get("name").getAsString().equals(name)) return value.get("value").getAsString();
        }
        return "";
    }
}
