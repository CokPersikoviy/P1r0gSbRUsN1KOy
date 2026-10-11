package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import ru.wilyfox.boss.*;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.widget.BossHudWidget;
import ru.wilyfox.client.level.LevelProgressStore;
import ru.wilyfox.client.popup.*;
import ru.wilyfox.client.protocol.*;
import java.util.*;

public final class BossTimerFiltersClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var original = ConfigManager.snapshot();
                var popUps = PopUpManager.getInstance();
                Object state = field(DiamondWorldProtocolClient.class, null, "STATE");
                var originalLevel = (LevelProgressStore) field(state.getClass(), state, "levelProgressStore");
                var originalTypes = new HashMap<>((Map<String,DwBossType>) field(state.getClass(), state, "bossTypes"));
                try {
                    var level = new LevelProgressStore(); level.updateCurrent(125, null, null);
                    DiamondWorldProtocolClient.bindLevelProgressStore(level);
                    SocialProtocolFixture.bossTypes(Map.of(
                            "hidden", new DwBossType("hidden", "Hidden", "STONE", 15, 0, 0, false),
                            "allowed", new DwBossType("allowed", "Allowed", "STONE", 100, 0, 0, false),
                            "high", new DwBossType("high", "High", "STONE", 240, 0, 0, false)));
                    var config = new BossWidgetConfig(); config.maxLevel = 120; config.matchPlayerLevel = true;
                    config.blacklist.add("hidden"); config.showSpawnedUntilKilled = true;
                    ConfigManager.get().bossWidget = config;
                    ConfigManager.get().popUps.active = true;
                    ConfigManager.get().popUps.bossSpawnEvent = ConfigManager.get().popUps.bossCaptureEvent = true;
                    popUps.removeSources(PopUpSource.BOSS_SPAWN, PopUpSource.BOSS_CAPTURE);
                    var repository = new BossRepository(); long now = System.currentTimeMillis() - 100;
                    repository.upsertProtocol("hidden", "Hidden", now, 15);
                    repository.upsertProtocol("allowed", "Allowed", now, 100);
                    repository.upsertProtocol("high", "High", now, 240);
                    var widget = new BossHudWidget(20, 20, HudLayer.CONTENT, repository);
                    check(names(widget).equals(Set.of("Allowed")), "Timer ignored blacklist or player-level cap");
                    var constructor = PopUpEventNotifier.class.getDeclaredConstructor(); constructor.setAccessible(true);
                    var notifier = constructor.newInstance(); notifier.bindBossRepository(repository);
                    checkSpawns(notifier);
                    check(count(PopUpSource.BOSS_SPAWN) == 1 && popUps.getVisibleNotifications(32).stream()
                            .anyMatch(n -> n.source().equals(PopUpSource.BOSS_SPAWN) && n.message().contains("Allowed")),
                            "Hidden or over-level boss generated a spawn pop-up");
                    capture("Hidden"); capture("Allowed"); capture("High");
                    check(count(PopUpSource.BOSS_CAPTURE) == 1, "Boss captures ignored timer filters");

                    config.blacklist.clear(); level.updateCurrent(300, null, null);
                    check(names(widget).equals(Set.of("Hidden", "Allowed", "High")), "Level-up did not raise the cap above the saved manual maximum");
                    checkSpawns(notifier);
                    check(count(PopUpSource.BOSS_SPAWN) == 1, "Unhiding a boss replayed a suppressed spawn event");
                    config.matchPlayerLevel = false;
                    check(names(widget).equals(Set.of("Hidden", "Allowed")) && config.maxLevel == 120, "Disabling auto cap lost the manual maximum");
                    config.matchPlayerLevel = true; level.updateCurrent(0, null, null);
                    check(names(widget).equals(Set.of("Hidden", "Allowed")), "Unknown player level did not use the manual maximum");
                    level.updateCurrent(75, null, null); config.minLevel = 20;
                    check(names(widget).isEmpty(), "Automatic maximum bypassed the minimum level");
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                finally {
                    popUps.removeSources(PopUpSource.BOSS_SPAWN, PopUpSource.BOSS_CAPTURE);
                    DiamondWorldProtocolClient.bindLevelProgressStore(originalLevel);
                    SocialProtocolFixture.bossTypes(originalTypes);
                    ConfigManager.restoreSnapshot(original);
                }
            });
        }
    }
    private static Set<String> names(BossHudWidget widget) throws ReflectiveOperationException {
        var method = BossHudWidget.class.getDeclaredMethod("computeVisibleBosses"); method.setAccessible(true);
        var result = new HashSet<String>(); for (var boss : (List<BossInfo>)method.invoke(widget)) result.add(boss.getName()); return result;
    }
    private static void checkSpawns(PopUpEventNotifier notifier) throws ReflectiveOperationException {
        var method = PopUpEventNotifier.class.getDeclaredMethod("checkBossSpawns"); method.setAccessible(true); method.invoke(notifier);
    }
    private static void capture(String name) throws ReflectiveOperationException {
        var method = AutoBossAnnouncer.class.getDeclaredMethod("publishBossCapture", BossMessageParser.BossCapture.class);
        method.setAccessible(true); method.invoke(null, new BossMessageParser.BossCapture(name, "Clan"));
    }
    private static long count(String source) { return PopUpManager.getInstance().getVisibleNotifications(32).stream().filter(n -> n.source().equals(source)).count(); }
    private static Object field(Class<?> type, Object instance, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(instance); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
