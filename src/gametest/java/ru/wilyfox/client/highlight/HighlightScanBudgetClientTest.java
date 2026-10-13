package ru.wilyfox.client.highlight;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.AABB;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.protocol.SocialProtocolFixture;
import java.util.*;

/** Real client chunks/entities, including updates during a partial scan and cluster-boundary parts. */
public final class HighlightScanBudgetClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var config = context.computeOnClient(client -> ConfigManager.snapshot());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                try {
                    ConfigManager.get().render.usefulItemsHighlight = true;
                    SocialProtocolFixture.location("PRISONEVO1", 2, "shaft_12");
                    call(UsefulWorldHighlightRenderHook.class, null, "shouldWaitForWorldContext", new Class<?>[0]);
                    client.player.setPos(client.player.getX(), 128, client.player.getZ());
                    verifyDisabledProfiling();
                    verifySlices(client);
                    verifyClusters(client);
                } finally {
                    SocialProtocolFixture.clear();
                    call(UsefulWorldHighlightRenderHook.class, null, "clearCache", new Class<?>[0]);
                }
            });
        } finally { context.runOnClient(client -> ConfigManager.restoreSnapshot(config)); }
    }

    private static void verifyDisabledProfiling() {
        if (ru.wilyfox.client.profiler.ModProfiler.getInstance().isEnabled()) return;
        try {
            var lookup = java.lang.invoke.MethodHandles.privateLookupIn(UsefulWorldHighlightRenderHook.class, java.lang.invoke.MethodHandles.lookup());
            var count = lookup.findStatic(UsefulWorldHighlightRenderHook.class, "count", java.lang.invoke.MethodType.methodType(void.class, String.class));
            var profile = lookup.findStatic(UsefulWorldHighlightRenderHook.class, "profile", java.lang.invoke.MethodType.methodType(ru.wilyfox.client.profiler.ModProfiler.Scope.class, String.class));
            var noop = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope(null);
            for (int i = 0; i < 2000; i++) { count.invokeExact("allocation-check"); var ignored = (ru.wilyfox.client.profiler.ModProfiler.Scope) profile.invokeExact("allocation-check"); }
            var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
            if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
            long id = Thread.currentThread().threadId(), start = bean.getThreadAllocatedBytes(id);
            for (int i = 0; i < 10000; i++) {
                count.invokeExact("allocation-check");
                check(noop == (ru.wilyfox.client.profiler.ModProfiler.Scope) profile.invokeExact("allocation-check"), "Disabled scope was not no-op");
            }
            long bytes = bean.getThreadAllocatedBytes(id) - start;
            check(bytes < 64_000, "Disabled highlight diagnostics allocated " + bytes + " bytes");
            System.out.println("Disabled highlight profiling: 10000 counter/scope pairs=" + bytes + " bytes");
        } catch (Throwable error) { throw new AssertionError(error); }
    }

    private static void verifySlices(Minecraft client) {
        call(UsefulWorldHighlightRenderHook.class, null, "clearCache", new Class<?>[0]);
        var chunk = client.level.getChunkAt(client.player.blockPosition());
        long key = chunk.getPos().pack();
        int minY = client.player.blockPosition().getY() - 20;
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        var saved = new LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        var first = new BlockPos(x0, minY, z0);
        var last = new BlockPos(x0 + 15, minY + 40, z0 + 15);
        var barrel = Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.INSTRUMENT, NoteBlockInstrument.FLUTE).setValue(NoteBlock.NOTE, 19);
        try {
            for (int y = minY; y <= minY + 40; y++) for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
                var pos = new BlockPos(x, y, z);
                saved.put(pos, chunk.getBlockState(pos));
                chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), 0);
            }
            chunk.setBlockState(first, barrel, 0); chunk.setBlockState(last, barrel, 0);
            field("lastScanCenter", client.player.blockPosition());
            var cursorClass = Arrays.stream(UsefulWorldHighlightRenderHook.class.getDeclaredClasses())
                    .filter(type -> type.getSimpleName().equals("ChunkScanCursor")).findFirst().orElseThrow();
            var ctor = cursorClass.getDeclaredConstructor(Minecraft.class, net.minecraft.world.level.chunk.LevelChunk.class);
            ctor.setAccessible(true); Object cursor = ctor.newInstance(client, chunk);
            int visited = (int) call(cursorClass, cursor, "advance", new Class<?>[]{Minecraft.class, int.class, long.class}, client, 8192, Long.MAX_VALUE);
            check(visited == 8192 && (int) get(cursor, "index") == 8192, "Chunk scan exceeded its deterministic block budget");
            check(((Map<?, ?>) get(cursor, "boxes")).size() == 1, "Slice read beyond its cursor or lost a barrel");
            Map<Long, Object> scans = map("BLOCK_SCAN_CURSORS");
            Set<Long> pending = set("PENDING_BLOCK_SCAN_KEYS");
            scans.put(key, cursor); pending.add(key);
            check(map("BLOCK_CHUNK_CACHE").isEmpty(), "Partial results were published");
            chunk.setBlockState(first, Blocks.AIR.defaultBlockState(), 0);
            UsefulWorldHighlightRenderHook.markBlockDirty(first);
            check(!scans.containsKey(key), "Block update retained an outdated partial scan");
            // Force a bulk update to exercise the full-rescan path, not only local patches.
            for (int i = 1; i <= 6; i++) UsefulWorldHighlightRenderHook.markBlockDirty(first.offset(i, 0, 0));
            int cx = chunk.getPos().x(), cz = chunk.getPos().z();
            call(UsefulWorldHighlightRenderHook.class, null, "processDirtyChunks",
                    new Class<?>[]{Minecraft.class, int.class, int.class, int.class, int.class}, client, cx, cx, cz, cz);
            check(map("BLOCK_CHUNK_CACHE").isEmpty() && pending.contains(key), "Bulk dirty update did a synchronous full scan");
            drain(client);
            Object result = map("BLOCK_CHUNK_CACHE").get(key);
            Map<?, ?> boxes = (Map<?, ?>) get(result, "boxesByBlockPos");
            check(boxes.size() == 1 && boxes.containsKey(last.asLong()) && !boxes.containsKey(first.asLong()), "Resumed scan published stale or incomplete results");
            var type = UsefulWorldHighlightRenderHook.HighlightBlockType.NORMAL_BARREL;
            AABB actual = (AABB) get(boxes.get(last.asLong()), "box");
            check(actual.equals(type.createBox(client.level, last, barrel)), "Incremental scan changed highlight geometry");
            // Start another slice, then unload the chunk. Neither cursor nor published data may survive.
            pending.add(key);
            call(UsefulWorldHighlightRenderHook.class, null, "processPendingChunkScans", new Class<?>[]{Minecraft.class}, client);
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(client.level, chunk);
            check(!pending.contains(key) && !scans.containsKey(key) && !map("BLOCK_CHUNK_CACHE").containsKey(key), "Unloaded chunk retained scan state");
            System.out.println("Highlight chunk budget: 8192-step slices, atomic publication, mid-scan/bulk updates, geometry and unload passed");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        finally { saved.forEach((pos, state) -> chunk.setBlockState(pos, state, 0)); call(UsefulWorldHighlightRenderHook.class, null, "clearCache", new Class<?>[0]); }
    }

    private static void verifyClusters(Minecraft client) {
        var entities = new ArrayList<Entity>();
        try {
            double x = client.player.getX() + 31, y = client.player.getY(), z = client.player.getZ();
            var interaction = new Interaction(EntityTypes.INTERACTION, client.level);
            // A wide interaction near the search boundary needs parts beyond the player's scan box.
            interaction.setPos(x, y, z); interaction.setBoundingBox(new AABB(x - .5, y, z - .5, x + 2, y + 1, z + .5));
            var stand = new ArmorStand(client.level, x + 2.5, y, z);
            var gold = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, client.level);
            gold.setPos(x + 2.8, y + .2, z); gold.setBoundingBox(new AABB(x + 2.6, y, z - .2, x + 3, y + .4, z + .2));
            var fire = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, client.level);
            fire.setPos(x + 3.2, y, z); fire.setBoundingBox(new AABB(x + 3, y, z - .2, x + 3.4, y + .4, z + .2));
            setModel(gold, "modelengine:fragment_1"); setModel(fire, "modelengine:internal_fire/1");
            entities.addAll(List.of(interaction, stand, gold, fire));
            int id = 4_100_000;
            for (Entity entity : entities) { entity.setId(id++); client.level.addEntity(entity); }
            collect(client);
            List<?> boxes = list("ENTITY_BOXES");
            AABB expected = interaction.getBoundingBox().minmax(stand.getBoundingBox()).minmax(gold.getBoundingBox()).minmax(fire.getBoundingBox()).inflate(.08, .12, .08);
            check(boxes.size() == 1 && get(boxes.getFirst(), "box").equals(expected), "Spatial index changed the legacy cluster box at scan/cell boundaries");
            setModel(fire, "other:internal_fire/1"); collect(client);
            check(list("ENTITY_BOXES").isEmpty(), "Changed model/namespace retained a cached crystal");
            setModel(fire, "modelengine:internal_fire/2");
            stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.STONE)); collect(client);
            check(list("ENTITY_BOXES").isEmpty(), "Changed equipment retained an empty-stand match");
            stand.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); collect(client);
            check(list("ENTITY_BOXES").size() == 1, "Updated crystal components did not reappear");
            client.level.removeEntity(gold.getId(), Entity.RemovalReason.DISCARDED); collect(client);
            check(list("ENTITY_BOXES").isEmpty(), "Unloaded display stayed in the spatial index");
            var worm = new ArmorStand(client.level, client.player.getX() + 1, client.player.getY(), client.player.getZ());
            worm.setId(4_100_010); worm.setCustomName(net.minecraft.network.chat.Component.literal("Червь"));
            entities.add(worm); client.level.addEntity(worm); collect(client);
            check(list("ENTITY_BOXES").size() == 1 && get(list("ENTITY_BOXES").getFirst(), "box").equals(worm.getBoundingBox().inflate(.1)),
                    "Worm name or highlight geometry changed");
            System.out.println("Highlight cluster index: legacy bounds, scan/cell boundaries, model namespace, equipment and entity unload passed");
        } finally { entities.forEach(entity -> client.level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED)); }
    }

    private static void setModel(Display.ItemDisplay display, String model) {
        var stack = new ItemStack(Items.STONE); stack.set(DataComponents.ITEM_MODEL, Identifier.parse(model));
        call(Display.ItemDisplay.class, display, "setItemStack", new Class<?>[]{ItemStack.class}, stack);
    }
    private static void collect(Minecraft client) {
        list("ENTITY_BOXES").clear();
        call(UsefulWorldHighlightRenderHook.class, null, "collectNearbyEntities", new Class<?>[]{Minecraft.class}, client);
    }
    private static void drain(Minecraft client) {
        for (int i = 0; i < 256 && !set("PENDING_BLOCK_SCAN_KEYS").isEmpty(); i++)
            call(UsefulWorldHighlightRenderHook.class, null, "processPendingChunkScans", new Class<?>[]{Minecraft.class}, client);
        check(set("PENDING_BLOCK_SCAN_KEYS").isEmpty(), "Scan queue did not finish");
    }
    @SuppressWarnings("unchecked") private static <T> Map<Long, T> map(String name) { return (Map<Long, T>) field(name, null); }
    @SuppressWarnings("unchecked") private static Set<Long> set(String name) { return (Set<Long>) field(name, null); }
    @SuppressWarnings("unchecked") private static List<Object> list(String name) { return (List<Object>) field(name, null); }
    private static Object field(String name, Object value) {
        try { var f = UsefulWorldHighlightRenderHook.class.getDeclaredField(name); f.setAccessible(true); if (value != null) f.set(null, value); return f.get(null); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static Object get(Object object, String name) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static Object call(Class<?> type, Object target, String name, Class<?>[] params, Object... args) {
        try { var method = type.getDeclaredMethod(name, params); method.setAccessible(true); return method.invoke(target, args); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
