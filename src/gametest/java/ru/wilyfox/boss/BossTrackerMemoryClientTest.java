package ru.wilyfox.boss;

import com.sun.management.ThreadMXBean;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import ru.wilyfox.client.protocol.BossTypeCatalog;
import ru.wilyfox.client.protocol.DwBossType;
import ru.wilyfox.utils.BossName;
import ru.wilyfox.utils.Formatting;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BossTrackerMemoryClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var originalCatalog = new LinkedHashMap<String, DwBossType>();
                BossTypeCatalog.snapshot().forEach(type -> originalCatalog.put(type.id(), type));
                var entities = new ArrayList<ArmorStand>();
                var repository = new BossRepository();
                var tracker = new BossTracker(repository);
                try {
                    for (int i = 0; i < 200; i++) {
                        var stand = new ArmorStand(client.level, 0, 100, 0);
                        stand.setId(4_000_000 + i);
                        stand.setCustomName(Component.literal("Декоративная подпись " + i));
                        client.level.addEntity(stand);
                        tracker.onEntityLoad(stand);
                        entities.add(stand);
                    }
                    tracker.onWorldTick(client.level);
                    var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
                    if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
                    long thread = Thread.currentThread().threadId();
                    long start = bean.getThreadAllocatedBytes(thread);
                    for (int tick = 0; tick < 200; tick++) tracker.onWorldTick(client.level);
                    long cachedBytes = bean.getThreadAllocatedBytes(thread) - start;
                    expect(cachedBytes < 512_000, "Unchanged decoration names allocated " + cachedBytes + " bytes");

                    var catalog = BossTypeCatalog.snapshot();
                    start = bean.getThreadAllocatedBytes(thread);
                    // Same workload without remembered misses, using the optimized parser. This
                    // is an allocation comparison, not a fragile wall-clock/FPS assertion.
                    for (int tick = 0; tick < 20; tick++) {
                        for (var stand : entities) {
                            String text = stand.getCustomName().getString();
                            BossName.getBossName(text);
                            BossName.resolveRegistryName(text, catalog);
                            Formatting.parseTimeToMillis(text);
                        }
                    }
                    long repeatedBytes = bean.getThreadAllocatedBytes(thread) - start;
                    expect(cachedBytes * 20 < repeatedBytes, "Remembered names did not reduce allocations");
                    System.out.println("BossTracker allocation regression: cached 200 ticks=" + cachedBytes
                            + " bytes; repeated parsing 20 ticks=" + repeatedBytes + " bytes");

                    // A previously rejected name must be rechecked when its text changes or
                    // when an unchanged English hologram gains a type in a later packet.
                    entities.getFirst().setCustomName(Component.literal("AUDIT FUTURE BOSS"));
                    tracker.onWorldTick(client.level);
                    expect(repository.getAll().isEmpty(), "Unknown decoration became a boss");
                    BossTypeCatalog.update(Map.of("audit_future_boss",
                            new DwBossType("audit_future_boss", "Audit Future Boss", "", 777, 0, 0, false)));
                    tracker.onWorldTick(client.level);
                    entities.get(1).setCustomName(Component.literal("00:40"));
                    tracker.onWorldTick(client.level);
                    expect(repository.getAll().stream().anyMatch(boss -> boss.getName().equals("Audit Future Boss")),
                            "A cached miss ignored a later boss registry packet");
                    entities.get(2).setCustomName(null);
                    tracker.onWorldTick(client.level);
                    entities.get(2).setCustomName(Component.literal("Кригер"));
                    entities.get(3).setCustomName(Component.literal("00:30"));
                    tracker.onWorldTick(client.level);
                    expect(repository.getAll().stream().anyMatch(boss -> boss.getName().equals("Кригер")),
                            "A cached decoration ignored an updated/delayed custom name");

                    client.level.removeEntity(entities.get(4).getId(), Entity.RemovalReason.DISCARDED);
                    tracker.onWorldTick(client.level);
                    expect(!checkedNames(tracker).containsKey(entities.get(4).getId()), "Unloaded entity retained text");
                    tracker.reset();
                    expect(checkedNames(tracker).isEmpty(), "Reset retained old-world names");
                } finally {
                    entities.forEach(entity -> client.level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED));
                    tracker.reset();
                    try {
                        var reset = BossTypeCatalog.class.getDeclaredMethod("resetForTesting");
                        reset.setAccessible(true);
                        reset.invoke(null);
                    } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
                    BossTypeCatalog.update(originalCatalog);
                }
            });
        }
    }

    private static Map<?, ?> checkedNames(BossTracker tracker) {
        try {
            var field = BossTracker.class.getDeclaredField("checkedNames");
            field.setAccessible(true);
            return (Map<?, ?>) field.get(tracker);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
