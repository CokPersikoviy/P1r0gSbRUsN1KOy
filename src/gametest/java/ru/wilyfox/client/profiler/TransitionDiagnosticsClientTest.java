package ru.wilyfox.client.profiler;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import ru.wilyfox.FrogHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import ru.wilyfox.client.chat.ChatDispatchQueue;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.protocol.SocialProtocolFixture;
import ru.wilyfox.client.quickaccess.QuickAccessItemConfig;
import ru.wilyfox.client.quickaccess.QuickAccessManager;
import ru.wilyfox.client.quickaccess.QuickAccessScreen;

public final class TransitionDiagnosticsClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        boolean originalDebug = context.computeOnClient(client -> ru.wilyfox.client.hud.config.ConfigManager.get().render.debug);
        context.runOnClient(client -> ru.wilyfox.client.hud.config.ConfigManager.get().render.debug = true);
        context.waitTicks(2);
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                for (int i = 0; i < 3; i++) {
                    var sample = ProfilerDiagnostics.captureSample(client);
                    FrogHelper.LOGGER.info("Transition audit: full diagnostic sample {} took {} ms, chunks={}, entities={}",
                            i, sample.captureNanos() / 1_000_000.0, sample.world().loadedChunks(), sample.world().entities());
                }
                try {
                    SocialProtocolFixture.location("PRISONEVO1", 2, "shaft_12");
                    tab(client, "PrisonEvo-1 #2");
                    tab(client, "§aPrisonEvo-1 #3");
                    if (!DiamondWorldProtocolClient.getCurrentServerDisplayName(Component.empty()).equals("PrisonEvo-1 #3")) {
                        throw new AssertionError("New TAB server was hidden by the previous serverinfo");
                    }
                    if (DiamondWorldProtocolClient.getCurrentServerInfo().mirror() != 2) {
                        throw new AssertionError("Display-only TAB update changed authenticated presence/gameplay server");
                    }
                    SocialProtocolFixture.location("PRISONEVO1", 4, "shaft_12");
                    tab(client, "Players 200; PrisonEvo-1 #3");
                    if (!DiamondWorldProtocolClient.getCurrentServerDisplayName(Component.empty()).equals("PrisonEvo-1 #4")) {
                        throw new AssertionError("Unchanged TAB identity replaced a newer serverinfo");
                    }
                    var profiler = ModProfiler.getInstance();
                    var snapshot = snapshot(profiler);
                    if (snapshot.lifetimeTimeline().stream().noneMatch(event -> event.event().equals("client/transition-packet")
                            && event.detail().contains("ClientboundTabListPacket: queueMs=")
                            && !event.detail().contains("queueMs=unknown") && event.detail().contains("handleMs="))) {
                        throw new AssertionError("Actual TAB packet handler did not record receipt-to-handler timings");
                    }
                    verifyQuickAccess(client);
                } finally {
                    SocialProtocolFixture.clear();
                    client.getConnection().handleTabListCustomisation(new ClientboundTabListPacket(Component.empty(), Component.empty()));
                    if (!DiamondWorldProtocolClient.getCurrentServerDisplayName(Component.empty()).isEmpty()) {
                        throw new AssertionError("Previous connection's TAB/server display survived state reset");
                    }
                }
            });
        } finally {
            context.runOnClient(client -> ru.wilyfox.client.hud.config.ConfigManager.get().render.debug = originalDebug);
            context.waitTicks(2);
        }
    }

    private static void tab(net.minecraft.client.Minecraft client, String footer) {
        var packet = new ClientboundTabListPacket(Component.empty(), Component.literal(footer));
        ModProfiler.getInstance().recordNetworkPacket("clientbound", packet);
        client.getConnection().handleTabListCustomisation(packet);
    }

    private static void verifyQuickAccess(net.minecraft.client.Minecraft client) {
        var profiler = ModProfiler.getInstance();
        var manager = QuickAccessManager.getInstance();
        boolean wasEnabled = profiler.isEnabled();
        var action = new QuickAccessItemConfig();
        action.command = "seed";
        var screen = new QuickAccessScreen() {
            @Override public QuickAccessItemConfig getHoveredItem() { return action; }
        };
        var blocked = field(ChatDispatchQueue.class, "blockedUntilMs");
        try {
            long previousBlocked = blocked.getLong(null);
            profiler.start();
            ChatDispatchQueue.enqueueCommand("fh-transition-pending", 30_000);
            blocked.setLong(null, System.currentTimeMillis() + 120_000);
            client.gui.setScreen(screen);
            field(QuickAccessManager.class, "activeScreen").set(manager, screen);
            field(QuickAccessManager.class, "heldOpen").setBoolean(manager, true);
            long before = sentPackets(profiler);
            int queued = ChatDispatchQueue.getDebugSnapshot().size();
            try {
                manager.release(client);
                if (sentPackets(profiler) != before + 1 || ChatDispatchQueue.getDebugSnapshot().size() != queued) {
                    throw new AssertionError("User action was queued/blocked behind automated chat instead of being sent immediately");
                }
                if (snapshot(profiler).lifetimeTimeline().stream().noneMatch(event -> event.event().equals("client/command/send"))) {
                    throw new AssertionError("Actual command send did not publish a timeline marker");
                }
            } finally { blocked.setLong(null, previousBlocked); }
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        finally {
            manager.forceClose(client);
            client.gui.setScreen(null);
            ChatDispatchQueue.removeQueuedCommandsContaining("fh-transition-pending");
            if (!wasEnabled) profiler.stop();
        }
    }

    private static long sentPackets(ModProfiler profiler) {
        return snapshot(profiler).counters().stream().filter(counter -> counter.name().equals("network/serverbound/packets"))
                .mapToLong(ModProfiler.CounterView::total).sum();
    }

    private static ModProfiler.ReportSnapshot snapshot(ModProfiler profiler) {
        try {
            var method = ModProfiler.class.getDeclaredMethod("snapshotLocked", ProfilerDiagnostics.FullDiagnostics.class);
            method.setAccessible(true);
            synchronized (profiler) {
                return (ModProfiler.ReportSnapshot) method.invoke(profiler, new Object[]{null});
            }
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static java.lang.reflect.Field field(Class<?> owner, String name) {
        try {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
