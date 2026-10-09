package ru.wilyfox.client.moduser;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import ru.wilyfox.client.protocol.SocialProtocolFixture;
import java.lang.reflect.*;
import java.net.URI;
import java.net.http.WebSocket;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Exercise the real sender without a remote API or real Discord webhook. */
public final class SocialMetadataClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            BackendSocialClient sender = new BackendSocialClient(URI.create("http://127.0.0.1"));
            List<SocialWire.SessionUpdate> sent = new ArrayList<>();
            AtomicReference<CompletableFuture<WebSocket>> pending = new AtomicReference<>();
            WebSocket socket = (WebSocket) Proxy.newProxyInstance(WebSocket.class.getClassLoader(), new Class<?>[]{WebSocket.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("sendText")) {
                            sent.add(SocialWire.JSON.fromJson(args[0].toString(), SocialWire.SessionUpdate.class));
                            return pending.get() != null ? pending.get() : CompletableFuture.completedFuture((WebSocket) proxy);
                        }
                        return null;
                    });
            try {
                set(sender, "socket", socket);
                SocialProtocolFixture.location("HUB", 0, "spawn_overworld");
                send(sender, 10_000);
                expect(sent.size() == 1, "Initial metadata was delayed");
                SocialProtocolFixture.location("HUB", 0, "market");
                send(sender, 10_050);
                expect(sent.size() == 1, "Backend one-frame-per-second limit was exceeded");
                send(sender, 11_100);
                expect(sent.size() == 2 && !sent.get(0).location().equals(sent.get(1).location()),
                        "Location waited for the ordinary two-second interval");
                var previous = sent.get(1);
                set(sender, "sentMetadata", new SocialWire.SessionUpdate(previous.version(), previous.type(), previous.modVersion(), 1, previous.location()));
                send(sender, 12_200);
                expect(sent.size() == 2, "Level-only change bypassed the progress interval");
                send(sender, 13_100);
                expect(sent.size() == 3, "Level update failed after the progress interval");
                pending.set(new CompletableFuture<>());
                SocialProtocolFixture.location("HUB", 0, "bay");
                send(sender, 14_200);
                SocialProtocolFixture.location("HUB", 0, "shaft_125");
                send(sender, 17_000);
                expect(sent.size() == 4, "Sender queued overlapping websocket writes");
                pending.get().complete(socket);
                pending.set(null);
                send(sender, 17_001);
                expect(sent.size() == 5 && !sent.get(3).location().equals(sent.get(4).location()),
                        "Latest location was lost while a websocket write was pending");
            } finally {
                sender.reset();
                SocialProtocolFixture.clear();
            }
        });
    }
    private static void send(BackendSocialClient target, long now) {
        try { var method = BackendSocialClient.class.getDeclaredMethod("sendMetadata", long.class); method.setAccessible(true); method.invoke(target, now); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void set(Object target, String name, Object value) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
