package ru.wilyfox.client.moduser;

import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import ru.wilyfox.client.chat.BackendChatMessage;
import ru.wilyfox.client.chat.ChatDock;
import ru.wilyfox.client.chat.ChatTab;
import ru.wilyfox.client.chat.ChatTabManager;
import ru.wilyfox.client.hud.config.ConfigManager;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real asynchronous HTTP reads must refresh a known ID, without duplicating its chat entry. */
public final class BackendChatRefreshClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        var tabs = ChatTabManager.getInstance();
        HttpServer server;
        try { server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); }
        catch (java.io.IOException error) { throw new AssertionError(error); }
        long timestamp = System.currentTimeMillis();
        var current = new AtomicReference<>(new BackendChatMessage(1, "Fox", "Image", timestamp, "discord", List.of()));
        var heads = new AtomicInteger();
        var rejected = new AtomicInteger();
        var tail = new AtomicReference<List<BackendChatMessage>>(List.of());
        server.createContext("/v1/chat", exchange -> {
            try {
                if (!"Bearer unit-test-session".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    rejected.incrementAndGet(); exchange.sendResponseHeaders(401, -1); return;
                }
                String query = exchange.getRequestURI().getRawQuery();
                boolean after = query != null && query.contains("after=");
                if (!after) heads.incrementAndGet();
                var page = new BackendChatClient.Page(1, after ? tail.get() : List.of(current.get()), false);
                byte[] body = SocialWire.JSON.toJson(page).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        server.start();
        var reader = new BackendChatClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), HttpClient.newHttpClient());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                tabs.clearBackendMessages(); ConfigManager.get().render.chatTimestamps = false;
                ChatDock.select(ChatDock.key(ChatTab.FH)); client.gui.setScreen(new ChatScreen("", false));
            });
            await(context, reader, () -> tabs.getMessages(ChatTab.FH).size() == 1);
            var previous = context.computeOnClient(client -> tabs.getMessages(ChatTab.FH).getFirst());
            check(!previous.component().getString().contains("[image.png]"), "Fixture started with an image");
            current.set(new BackendChatMessage(1, "Fox", "Image", timestamp, "discord",
                    List.of(new BackendChatMessage.Attachment("1559187699896950924", "image.png", 367, 197))));
            context.runOnClient(client -> reader.changed(1));
            await(context, reader, () -> tabs.getMessages(ChatTab.FH).getFirst().component().getString().contains("[image.png]"));
            context.runOnClient(client -> {
                var entries = tabs.getMessages(ChatTab.FH);
                var updated = entries.getFirst();
                check(entries.size() == 1 && heads.get() == 2, "Known ID was duplicated or recent page was not refreshed");
                check(updated.timestamp().equals(previous.timestamp()) && updated.receivedNanos() == previous.receivedNanos()
                        && updated.message().addedTime() == previous.message().addedTime(), "Repair reset timestamp or message visibility age");
            });
            // A newer cursor must still read the tail, even if an older ID also announces a repair.
            tail.set(List.of(new BackendChatMessage(2, "Player", "Next message", timestamp)));
            context.runOnClient(client -> { reader.changed(1); reader.changed(2); });
            await(context, reader, () -> tabs.getMessages(ChatTab.FH).size() == 2);
            check(rejected.get() == 0, "Fixture request lost the bearer");
            System.out.println("FH HTTP chat: known-ID image repair and newer-cursor catch-up passed");
        } finally {
            context.runOnClient(client -> { reader.reset(); client.gui.setScreen(null); tabs.clearBackendMessages(); ConfigManager.restoreSnapshot(original); });
            server.stop(0);
        }
    }
    private static void await(ClientGameTestContext context, BackendChatClient reader, BooleanSupplier condition) {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (context.computeOnClient(client -> { reader.tick(true, "unit-test-session", "Fox"); return condition.getAsBoolean(); })) return;
            context.waitTicks(2);
        }
        throw new AssertionError("FH HTTP chat refresh did not arrive within five seconds");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
