package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;
import ru.wilyfox.bridge.BossHealthOverlayAccessor;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.protocol.DwBossType;
import ru.wilyfox.client.protocol.SocialProtocolFixture;

import java.util.Map;
import java.util.UUID;

public final class LowHpMessageClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var config = ConfigManager.get().bossRespawnMessages;
                boolean local = config.lowHealthMessage;
                boolean clan = config.lowHealthClanMessage;
                int percent = config.lowHealthPercent;
                int cooldown = config.lowHealthCooldownSeconds;
                var highId = new UUID(0, 0);
                var lowId = new UUID(0, 1);
                try {
                    clearState();
                    config.lowHealthMessage = true;
                    config.lowHealthClanMessage = true;
                    config.lowHealthPercent = 20;
                    config.lowHealthCooldownSeconds = 10;
                    SocialProtocolFixture.location("PRISONEVO1", 2, "boss_RatKing");
                    SocialProtocolFixture.bossTypes(Map.of(
                            "Krieger", new DwBossType("Krieger", "Кригер", "", 15, 0, 0, false),
                            "RatKing", new DwBossType("RatKing", "Крысиный Король", "", 200, 0, 0, false)));
                    for (String location : new String[]{"boss_RatKing", "bossRatKing", "boss__RatKing"}) {
                        SocialProtocolFixture.location("PRISONEVO1", 2, location);
                        var currentBoss = ru.wilyfox.client.protocol.DiamondWorldProtocolClient.getCurrentBossType();
                        if (currentBoss == null || !currentBoss.id().equals("RatKing")) fail("Location ID lost the boss registry ID: " + location);
                    }
                    SocialProtocolFixture.location("PRISONEVO1", 2, "boss_RatKing");
                    addBar(client, highId, "КРИГЕР 800❤ (Frogs42)", 0.8f);
                    addBar(client, lowId, "✦ RAT KING 125❤ (Clan 2)", 0.2f);
                    var bars = ((BossHealthOverlayAccessor) client.gui.hud.getBossOverlay()).froghelper$getEvents();
                    if (!bars.getFirst().getId().equals(highId)) fail("Fixture must place the healthy boss before the low-HP boss");

                    int localBefore = localMessages();
                    int queuedBefore = ChatDispatchQueue.getDebugSnapshot().size();
                    check();
                    if (localMessages() != localBefore + 1) fail("Low boss after the healthy boss did not publish exactly one local message at 20% HP");
                    String published = ChatTabManager.getInstance().getMessages(ChatTab.ALL).stream()
                            .map(entry -> entry.component().getString())
                            .filter(text -> text.contains("Крысиный Король [200]") && text.contains("125 HP (20%)"))
                            .findFirst().orElseThrow(() -> new AssertionError("Local message had the wrong boss, health or percentage"));
                    if (published.contains("Frogs42") || published.contains("Clan 2")) fail("Clan suffix was interpreted as boss health or name");
                    if (ChatDispatchQueue.getDebugSnapshot().size() != queuedBefore + 1) fail("Clan message not queued");
                    String clanText = pendingClanText();
                    if (!clanText.contains("Крысиный Король [200]") || !clanText.contains("125 HP (20%)")) fail("Clan message content differed from local HP announcement");
                    check();
                    if (localMessages() != localBefore + 1 || ChatDispatchQueue.getDebugSnapshot().size() != queuedBefore + 1) fail("Cooldown failed to suppress repeated messages");

                    clearState();
                    config.lowHealthPercent = 19;
                    check();
                    if (localMessages() != localBefore + 1) fail("Boss above HP threshold was announced");
                    config.lowHealthPercent = 20;
                    config.lowHealthMessage = false;
                    check();
                    if (localMessages() != localBefore + 1 || ChatDispatchQueue.getDebugSnapshot().size() != queuedBefore + 2) fail("Clan-only setting did not respect the local toggle");
                    clearState();
                    config.lowHealthClanMessage = false;
                    check();
                    if (ChatDispatchQueue.getDebugSnapshot().size() != queuedBefore + 2) fail("Disabled low HP feature still queued a message");

                    config.lowHealthMessage = true;
                    SocialProtocolFixture.location("PRISONEVO1", 2, "shaft_12");
                    check();
                    if (localMessages() != localBefore + 1) fail("Non-boss location produced a stale boss announcement");
                } finally {
                    client.getConnection().handleBossUpdate(ClientboundBossEventPacket.createRemovePacket(highId));
                    client.getConnection().handleBossUpdate(ClientboundBossEventPacket.createRemovePacket(lowId));
                    clearState();
                    clearTestQueue();
                    SocialProtocolFixture.clear();
                    config.lowHealthMessage = local;
                    config.lowHealthClanMessage = clan;
                    config.lowHealthPercent = percent;
                    config.lowHealthCooldownSeconds = cooldown;
                }
            });
        }
    }

    private static void addBar(Minecraft client, UUID id, String title, float progress) {
        var bar = new LerpingBossEvent(id, Component.literal(title), progress,
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS, false, false, false);
        client.getConnection().handleBossUpdate(ClientboundBossEventPacket.createAddPacket(bar));
    }

    private static int localMessages() {
        return (int) ChatTabManager.getInstance().getMessages(ChatTab.ALL).stream()
                .filter(entry -> entry.component().getString().contains("Крысиный Король [200]"))
                .count();
    }

    private static String pendingClanText() {
        try {
            var field = ChatDispatchQueue.class.getDeclaredField("QUEUE");
            field.setAccessible(true);
            var queued = ((java.util.Deque<?>) field.get(null)).getLast();
            var content = queued.getClass().getDeclaredMethod("content");
            content.setAccessible(true);
            return (String) content.invoke(queued);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static void clearTestQueue() {
        try {
            var field = ChatDispatchQueue.class.getDeclaredField("QUEUE");
            field.setAccessible(true);
            ((java.util.Deque<?>) field.get(null)).clear();
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static void clearState() { invoke("clearState"); }
    private static void check() { invoke("checkLowHealthMessages"); }
    private static void invoke(String name) {
        try {
            var method = AutoBossAnnouncer.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(null);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
    private static void fail(String message) { throw new AssertionError(message); }
}
