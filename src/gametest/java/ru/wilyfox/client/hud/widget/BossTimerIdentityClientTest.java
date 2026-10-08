package ru.wilyfox.client.hud.widget;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.boss.BossRepository;
import ru.wilyfox.boss.BossTracker;
import ru.wilyfox.client.hud.config.BossTimerSourceMode;
import ru.wilyfox.client.hud.config.BossWidgetConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;

import java.util.List;

public final class BossTimerIdentityClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                BossWidgetConfig original = ConfigManager.get().bossWidget;
                var config = new BossWidgetConfig();
                ConfigManager.get().bossWidget = config;
                var repository = new BossRepository();
                var tracker = new BossTracker(repository);
                var name = new ArmorStand(client.level, client.player.getX(), client.player.getY(), client.player.getZ());
                name.setId(3_000_001);
                name.setCustomName(Component.literal("§bБессмертный Легион"));
                var timer = new ArmorStand(client.level, client.player.getX(), client.player.getY(), client.player.getZ());
                timer.setId(3_000_002);
                timer.setCustomName(Component.literal("§f00:19"));
                try {
                    client.level.addEntity(name);
                    client.level.addEntity(timer);
                    tracker.onEntityLoad(name);
                    tracker.onEntityLoad(timer);
                    tracker.onWorldTick(client.level);
                    var widget = new BossHudWidget(20, 20, HudLayer.CONTENT, repository);
                    assertOneBoss(widget, 105);

                    repository.upsertProtocol("ImmortalLegion", "Бессмертныи легион",
                            System.currentTimeMillis() + 19_000L, 130);
                    assertOneBoss(widget, 130);
                    config.sourceMode = BossTimerSourceMode.PROTOCOL_ONLY;
                    assertOneBoss(widget, 130);

                    repository.updateProtocolMetadata("ImmortalLegion", "Бессмертныи легион", 130);
                    config.sourceMode = BossTimerSourceMode.WORLD_ONLY;
                    assertOneBoss(widget, 130);
                } finally {
                    client.level.removeEntity(name.getId(), Entity.RemovalReason.DISCARDED);
                    client.level.removeEntity(timer.getId(), Entity.RemovalReason.DISCARDED);
                    tracker.reset();
                    ConfigManager.get().bossWidget = original;
                }
            });
        }
    }

    private static void assertOneBoss(BossHudWidget widget, int level) {
        try {
            var compute = BossHudWidget.class.getDeclaredMethod("computeVisibleBosses");
            compute.setAccessible(true);
            List<?> bosses = (List<?>) compute.invoke(widget);
            if (bosses.size() != 1 || ((BossInfo) bosses.getFirst()).getLevel() != level) {
                throw new AssertionError("Expected one visible Legion at level " + level + ", got " + bosses);
            }
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
