package ru.wilyfox.client.profiler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sun.management.ThreadMXBean;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import java.lang.management.ManagementFactory;

public final class RenderProfilerAllocationClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var profiler = ModProfiler.getInstance();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                profiler.stop(); profiler.reset();
                var entity = new ItemDisplayEntityRenderState(); entity.entityType = EntityTypes.ITEM_DISPLAY;
                var block = new BlockEntityRenderState(); block.blockEntityType = BlockEntityTypes.JIGSAW; block.blockPos = BlockPos.ZERO;
                var pose = new PoseStack(); var nodes = new SubmitNodeStorage(); var camera = new CameraRenderState();
                var dispatcher = client.getEntityRenderDispatcher(); var blocks = client.getBlockEntityRenderDispatcher();
                for (int i=0;i<30000;i++) {
                    dispatcher.submit(entity, camera, 0, 0, 0, pose, nodes);
                    blocks.submit(block, pose, nodes, camera);
                }
                var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
                if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
                long id = Thread.currentThread().threadId(), start = bean.getThreadAllocatedBytes(id);
                for (int i=0;i<10000;i++) {
                    dispatcher.submit(entity, camera, 0, 0, 0, pose, nodes);
                    blocks.submit(block, pose, nodes, camera);
                }
                long bytes = bean.getThreadAllocatedBytes(id)-start;
                check(bytes < 128_000, "Disabled renderer profiling still allocated argument arrays/boxed coordinates: " + bytes);
                profiler.start();
                dispatcher.submit(entity, camera, 0, 0, 0, pose, nodes);
                blocks.submit(block, pose, nodes, camera);
                int depth = depth(profiler);
                var invalid = new EntityRenderState(); invalid.entityType = EntityTypes.COW;
                try { dispatcher.submit(invalid, camera, 0, 0, 0, pose, nodes); throw new AssertionError("Invalid renderer fixture did not throw"); }
                catch (net.minecraft.ReportedException expected) { check(depth(profiler)==depth,"Entity exception leaked a profiler scope"); }
                var invalidBlock = new BlockEntityRenderState(); invalidBlock.blockEntityType=BlockEntityTypes.CHEST; invalidBlock.blockPos=BlockPos.ZERO;
                try { blocks.submit(invalidBlock,pose,nodes,camera); throw new AssertionError("Invalid block renderer fixture did not throw"); }
                catch (net.minecraft.ReportedException expected) { check(depth(profiler)==depth,"Block entity exception leaked a profiler scope"); }
                var report = report(profiler);
                check(report.contains("render/entity/minecraft_item_display"),"Entity profiling coverage was lost");
                check(report.contains("render/blockEntity/minecraft_jigsaw"),"Early-return block entity coverage was lost");
                System.out.println("Renderer profiling: 20000 disabled calls="+bytes+" bytes; enabled coverage and exception cleanup passed");
                profiler.stop();
            });
        } finally { context.runOnClient(client -> profiler.stop()); }
    }
    private static int depth(ModProfiler profiler) {
        try { var field=ModProfiler.class.getDeclaredField("activeScopes"); field.setAccessible(true);
            return ((java.util.Deque<?>)((ThreadLocal<?>)field.get(profiler)).get()).size();
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static String report(ModProfiler profiler) {
        try { var method=ModProfiler.class.getDeclaredMethod("snapshotLocked",ProfilerDiagnostics.FullDiagnostics.class); method.setAccessible(true);
            synchronized (profiler) { return profiler.buildMarkdownReport((ModProfiler.ReportSnapshot)method.invoke(profiler,new Object[]{null})); }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
