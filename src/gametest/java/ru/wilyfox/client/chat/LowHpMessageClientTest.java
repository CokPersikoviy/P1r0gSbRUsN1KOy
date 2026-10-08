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
import ru.wilyfox.client.hud.config.LowHpMessageElement;
import ru.wilyfox.client.hud.config.LowHpMessageFormatConfig;
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
                var originalFormat = config.lowHealthFormat;
                var highId = new UUID(0, 0);
                var lowId = new UUID(0, 1);
                try {
                    clearState();
                    config.lowHealthMessage = true;
                    config.lowHealthClanMessage = true;
                    config.lowHealthPercent = 20;
                    config.lowHealthCooldownSeconds = 10;
                    config.lowHealthFormat = new LowHpMessageFormatConfig();
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
                            .filter(text -> text.contains("Крысиный Король [200]") && text.contains("125❤ (20%)"))
                            .findFirst().orElseThrow(() -> new AssertionError("Local message had the wrong boss, health or percentage"));
                    if (published.contains("Frogs42") || !published.endsWith(" [Clan 2]")) fail("Bossbar label was lost or taken from another boss");
                    if (ChatDispatchQueue.getDebugSnapshot().size() != queuedBefore + 1) fail("Clan message not queued");
                    String clanText = pendingClanText();
                    String clanPlain = stripClanColors(clanText);
                    // Local display may add a timestamp; it must retain the entire queued announcement.
                    if (!clanText.startsWith("@") || !published.endsWith(clanPlain.substring(1))) fail("Clan message content differed from local HP announcement");
                    if (!clanText.contains("&c125❤") || clanText.contains("§")) fail("Clan message did not use the server color-code format");
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

                    clearState();
                    config.lowHealthClanMessage = true;
                    SocialProtocolFixture.location("PRISONEVO1", 2, "boss_RatKing");
                    bars.stream().filter(bar -> bar.getId().equals(lowId)).findFirst().orElseThrow()
                            .setName(Component.literal("RAT KING 125❤ (§7Стадия 2/4§r)"));
                    check();
                    if (localMessages() != localBefore + 2) fail("Stage label prevented the low HP announcement");
                    String stageMessage = pendingClanText();
                    String stagePlain = stripClanColors(stageMessage);
                    if (!stagePlain.endsWith(" [Стадия 2/4]") || !stagePlain.contains("125❤ (20%)")) fail("Stage label missing from clan announcement");
                    if (ChatTabManager.getInstance().getMessages(ChatTab.ALL).stream()
                            .noneMatch(entry -> entry.component().getString().endsWith(stagePlain.substring(1)))) fail("Stage label missing from local announcement");

                    clearState();
                    int beforeHidden = ChatDispatchQueue.getDebugSnapshot().size();
                    for (var part : new LowHpMessageElement[]{LowHpMessageElement.SERVER, LowHpMessageElement.NAME,
                            LowHpMessageElement.LEVEL, LowHpMessageElement.STAGE}) config.lowHealthFormat.element(part).visible = false;
                    config.lowHealthFormat.element(LowHpMessageElement.HEALTH).colorCode = "§A";
                    config.lowHealthFormat.element(LowHpMessageElement.PERCENT).colorCode = "&b";
                    check();
                    String hiddenText = pendingClanText();
                    if (!stripClanColors(hiddenText).equals("@125❤ (20%)") || !hiddenText.contains("&a125❤")
                            || !hiddenText.contains("&b(20%)")) fail("Visibility or configured colors ignored by clan publisher");
                    var compactLocal = ChatTabManager.getInstance().getMessages(ChatTab.ALL).getLast().component();
                    if (!compactLocal.getString().endsWith("125❤ (20%)")) fail("Hidden elements remained in local announcement");
                    clearState();
                    for (var part : LowHpMessageElement.values()) config.lowHealthFormat.element(part).visible = false;
                    check();
                    if (ChatDispatchQueue.getDebugSnapshot().size() != beforeHidden + 1) fail("Empty constructor queued a blank announcement");
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
                    config.lowHealthFormat = originalFormat;
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

    private static String stripClanColors(String message) {
        return message.replaceAll("&[0-9a-fr]", "");
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
