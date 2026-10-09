package ru.wilyfox.client.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LocationUpdateClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        verifyJoin(context);
        verifyRespawn(context);
        var router = new ProtocolRouter();
        var local = new ProtocolState();
        var handled = new AtomicBoolean();
        Minecraft minecraft = context.computeOnClient(client -> client);
        context.runOnClient(client -> client.execute(() -> {
            // This is a Minecraft packet task. Reentrant execute() really defers work here.
            var deferred = new AtomicBoolean();
            client.execute(() -> deferred.set(true));
            expect(!deferred.get(), "Test must exercise the actual reentrant client queue");
            int pending = client.getPendingTasksCount();
            ProtocolTransport.receivePayload(client, local, router, payload("bay"));
            expect(local.currentGameLocation != null && local.currentGameLocation.id().equals("bay"),
                    "Location was still deferred behind another client task");
            expect(client.getPendingTasksCount() == pending, "Receiver enqueued a duplicate client task");
            ProtocolTransport.receivePayload(client, local, router, payload("market"));
            expect(local.currentGameLocation.id().equals("market"), "Consecutive payloads were reordered");
            local.resetRuntimeState(); handled.set(true);
        }));
        context.waitTicks(2);
        context.runOnClient(client -> {
            expect(handled.get(), "Client packet task did not run");
            expect(local.currentGameLocation == null, "Payload from before disconnect revived stale location");
        });
        byte[] borrowed = payload("shaft_125");
        ProtocolTransport.receivePayload(minecraft, local, router, borrowed); // Test thread, outside the client.
        Arrays.fill(borrowed, (byte) 0);
        context.waitTicks(2);
        context.runOnClient(client -> expect(local.currentGameLocation != null && local.currentGameLocation.id().equals("shaft_125"),
                "Off-thread fallback failed to preserve its owned payload"));

        Gson gson = HudConfigCodec.createGson();
        var original = context.computeOnClient(client -> gson.fromJson(gson.toJson(ConfigManager.get()), HudConfig.class));
        ProtocolState live = (ProtocolState) field(null, DiamondWorldProtocolClient.class, "STATE");
        var oldLocation = live.currentGameLocation;
        long oldContext = live.worldContextRevision, oldFreshness = live.gameLocationRevision;
        var renderer = Client.getInstance().getHudRenderer();
        var layoutHandled = new AtomicBoolean();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var config = HudConfigCodec.decode(gson, JsonParser.parseString("{\"mainLayout\":{\"widgets\":[\"BossHudWidget\"]}}"));
                var base = new WidgetLayoutConfig(); base.x = 120; base.y = 20; base.scale = 1f;
                config.widgetLayouts.put("BossHudWidget", base);
                var layout = new LocationWidgetLayoutConfig(); layout.widgets.add("BossHudWidget"); layout.locationVisibility.add("bay");
                var placement = new WidgetLayoutConfig(); placement.x = 40; placement.y = 60; placement.scale = 1.4f;
                layout.placements.put("BossHudWidget", placement); config.locationLayouts.put("fishing", layout);
                write(null, ConfigManager.class, "CONFIG", config); ConfigManager.setEditorLayout(null);
                live.currentGameLocation = new DwGameLocation("market"); renderer.refreshLayout();
                client.execute(() -> {
                    ProtocolTransport.receivePayload(client, live, router, payload("bay"));
                    renderer.renderLayer(HudLayer.CONTENT, new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO);
                    var boss = (AbstractWidget) renderer.getWidgets().stream().filter(w -> w.getClass().getSimpleName().equals("BossHudWidget")).findFirst().orElseThrow();
                    expect(boss.getStartX() == 40 && boss.getStartY() == 60 && boss.getScale() == 1.4f,
                            "HUD did not apply the location at the first render after the received packet");
                    layoutHandled.set(true);
                });
            });
            context.waitTicks(2);
            context.runOnClient(client -> expect(layoutHandled.get(), "Location layout packet task did not run"));
        } finally {
            context.runOnClient(client -> {
                write(null, ConfigManager.class, "CONFIG", original); ConfigManager.setEditorLayout(null);
                live.currentGameLocation = oldLocation; live.worldContextRevision = oldContext; live.gameLocationRevision = oldFreshness;
                ConfigManager.save(); renderer.refreshLayout();
            });
        }
    }
    private static void verifyJoin(ClientGameTestContext context) {
        var live = (ProtocolState) field(null, DiamondWorldProtocolClient.class, "STATE");
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var listener = client.getConnection();
                var owner = net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl.class;
                Object oldServer = field(listener, owner, "serverData");
                long oldHandshake = live.lastHandshakeAt, oldPayload = live.lastPayloadAt;
                boolean oldReceived = live.receivedEvoPlusPayload;
                var sent = new java.util.ArrayList<DwHandshakePayload>();
                var sender = (net.fabricmc.fabric.api.networking.v1.PacketSender) java.lang.reflect.Proxy.newProxyInstance(
                        net.fabricmc.fabric.api.networking.v1.PacketSender.class.getClassLoader(),
                        new Class<?>[]{net.fabricmc.fabric.api.networking.v1.PacketSender.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("sendPacket") && args[0] instanceof DwHandshakePayload handshake) sent.add(handshake);
                            return null;
                        });
                try {
                    write(listener, owner, "serverData", new net.minecraft.client.multiplayer.ServerData("DW", "play.diamondworld.ru", net.minecraft.client.multiplayer.ServerData.Type.OTHER));
                    live.receivedEvoPlusPayload = true;
                    live.lastPayloadAt = System.currentTimeMillis();
                    live.lastHandshakeAt = System.currentTimeMillis();
                    int pending = client.getPendingTasksCount();
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.invoker().onPlayReady(listener, sender, client);
                    expect(sent.size() == 1, "Healthy previous session blocked the JOIN handshake until the stale timeout");
                    expect(client.getPendingTasksCount() == pending, "JOIN handshake was deferred to another client task");
                    expect(!live.receivedEvoPlusPayload && live.lastPayloadAt == 0 && live.lastHandshakeAt > 0,
                            "JOIN retained the previous session's handshake bookkeeping");
                    expect(sent.get(0).fingerprint().equals(DwHandshakeFingerprint.generate()), "JOIN changed handshake format");
                    live.receivedEvoPlusPayload = true;
                    live.lastPayloadAt = System.currentTimeMillis();
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.invoker().onPlayReady(listener, sender, client);
                    expect(sent.size() == 2, "A new play JOIN must always perform its own immediate handshake");
                    write(listener, owner, "serverData", oldServer);
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.invoker().onPlayReady(listener, sender, client);
                    expect(sent.size() == 2, "Non-DW join triggered an unrelated handshake");
                } finally {
                    write(listener, owner, "serverData", oldServer);
                    live.lastHandshakeAt = oldHandshake; live.lastPayloadAt = oldPayload;
                    live.receivedEvoPlusPayload = oldReceived;
                }
            });
        }
    }
    private static void verifyRespawn(ClientGameTestContext context) {
        var live = (ProtocolState) field(null, DiamondWorldProtocolClient.class, "STATE");
        var received = new java.util.concurrent.atomic.AtomicInteger();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(DwHandshakePayload.TYPE,
                (packet, serverContext) -> received.incrementAndGet());
        try (var world = context.worldBuilder().create()) {
            var listener = context.computeOnClient(client -> client.getConnection());
            var owner = net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl.class;
            var oldServer = field(listener, owner, "serverData");
            long oldHandshake = live.lastHandshakeAt, oldPayload = live.lastPayloadAt;
            boolean oldReceived = live.receivedEvoPlusPayload;
            try {
                context.runOnClient(client -> {
                    write(listener, owner, "serverData", new net.minecraft.client.multiplayer.ServerData("DW", "play.diamondworld.ru", net.minecraft.client.multiplayer.ServerData.Type.OTHER));
                    live.receivedEvoPlusPayload = true;
                    live.lastPayloadAt = System.currentTimeMillis();
                    live.lastHandshakeAt = System.currentTimeMillis() - 10_000;
                    var server = client.getSingleplayerServer();
                    server.execute(() -> {
                        var player = server.getPlayerList().getPlayers().getFirst();
                        var packet = new net.minecraft.network.protocol.game.ClientboundRespawnPacket(
                                player.createCommonSpawnInfo(player.level()), (byte) 3);
                        // A proxy may use paired Respawns without a new Login/JOIN.
                        player.connection.send(packet); player.connection.send(packet);
                    });
                });
                context.waitTicks(6);
                context.runOnClient(client -> {
                    expect(received.get() == 1, "Paired Respawn packets must produce exactly one prompt refresh handshake");
                    expect(!live.worldRefreshPending, "Respawn refresh was never consumed");
                });
                context.waitTicks(3);
                expect(received.get() == 1, "Respawn refresh repeated on subsequent ticks");
            } finally {
                context.runOnClient(client -> {
                    write(listener, owner, "serverData", oldServer);
                    live.lastHandshakeAt = oldHandshake; live.lastPayloadAt = oldPayload;
                    live.receivedEvoPlusPayload = oldReceived;
                    live.worldRefreshPending = false; live.locationBeforeWorldRefresh = null;
                });
            }
        } finally {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.unregisterGlobalReceiver(DwHandshakePayload.TYPE.id());
        }
    }
    private static byte[] payload(String id) {
        var buffer = Unpooled.buffer();
        try {
            DwProtocolCodec.writeString(buffer, "statisticinfo"); DwProtocolCodec.writeVarInt(buffer, 1);
            DwProtocolCodec.writeString(buffer, "gameLocation"); DwProtocolCodec.writeString(buffer, "\"" + id + "\"");
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes); return bytes;
        } finally { buffer.release(); }
    }
    private static Object field(Object target, Class<?> type, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void write(Object target, Class<?> type, String name, Object value) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
