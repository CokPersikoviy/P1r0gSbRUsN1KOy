package ru.wilyfox.client.profiler;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import ru.wilyfox.client.hud.config.ConfigManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Collect real renderer states without altering display interpolation, models or culling flags. */
public final class DisplayProbeClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var entities = new ArrayList<Entity>();
        boolean debug = context.computeOnClient(client -> ConfigManager.get().render.debug);
        var profiler = ModProfiler.getInstance();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.get().render.debug = false;
                profiler.stop();
                client.gui.setScreen(null);
                var first = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, client.level);
                first.setId(4_200_000); first.setPos(client.player.getX() + 2, client.player.getY(), client.player.getZ());
                first.getSlot(0).set(new ItemStack(Items.BONE));
                var second = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, client.level);
                second.setId(4_200_001); second.setPos(client.player.getX() + 3, client.player.getY(), client.player.getZ());
                var stack = new ItemStack(Items.BONE);
                stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(271f), List.of(), List.of(), List.of()));
                second.getSlot(0).set(stack);
                invoke(Display.class, second, "setWidth", new Class<?>[]{float.class}, 2f);
                invoke(Display.class, second, "setHeight", new Class<?>[]{float.class}, 3f);
                var block = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, client.level);
                block.setId(4_200_002); block.setPos(client.player.getX() + 3, client.player.getY(), client.player.getZ());
                entities.addAll(List.of(first, second, block)); entities.forEach(client.level::addEntity);
                check(!first.affectedByCulling() && second.affectedByCulling(), "Fixture culling flags incorrect");
                check(DisplayProbe.start(client, "../../escape").startsWith("Usage:"), "Probe accepted a path label");
                check(DisplayProbe.start(client, "native-front").contains("20s recording"), "Probe command did not start");
                check(DisplayProbe.start(client, "second").contains("already running"), "Concurrent probe replaced the first one");
                Object session = active();
                set(session, "measurementStart", System.nanoTime() - 1_000_000_000L);
                set(session, "nextSampleAt", 0L);
            });
            context.waitTicks(25);
            context.runOnClient(client -> {
                Object session = active();
                @SuppressWarnings("unchecked") var samples = (List<DisplayProbe.Sample>) get(session, "samples");
                check(samples.size() >= 2, "World hook did not collect once-per-second samples");
                var counts = samples.getLast().counts();
                check(counts.displays() == 3 && counts.items() == 2 && counts.noCull() == 1
                        && counts.bone() == 2 && counts.custom() == 1, "Display type/carrier/culling counters are wrong: " + counts);
                check(counts.prepared() > 0, "Probe ran after renderer states were discarded");
                check(!profiler.isEnabled(), "Standalone probe started detailed profiling/JFR");
                check(!((Display) entities.getFirst()).affectedByCulling() && ((Display) entities.get(1)).affectedByCulling(),
                        "Diagnostic probe changed vanilla culling");
                set(session, "measurementStart", System.nanoTime() - 21_000_000_000L);
            });
            context.waitTicks(2);
            Path saved = null;
            for (int i = 0; i < 40 && saved == null; i++) {
                context.waitTicks(1);
                saved = context.computeOnClient(client -> (Path) field(DisplayProbe.class, null, "lastSaved"));
            }
            check(saved != null && Files.isRegularFile(saved), "Probe report was not saved asynchronously");
            String report = Files.readString(saved);
            check(report.contains("native-front") && report.contains("result: complete") && report.contains("minecraft:bone")
                    && report.contains("not a safe decision to hide the model") && report.contains("Item prepared"), "Report omitted counts or their interpretation");
            context.runOnClient(client -> {
                check(active() == null, "Completed probe kept running");
                check(DisplayProbe.start(client, "disconnect-test").contains("20s recording"), "A completed probe blocked the next one");
                invoke(DisplayProbe.class, null, "finish", new Class<?>[]{net.minecraft.client.Minecraft.class, String.class}, client, "disconnect");
                check(active() == null, "Disconnect retained world/probe state");
            });
            System.out.println("Display probe: real renderer states, type/culling/model counters, idle profiler, label validation, atomic async save and disconnect passed: " + saved);
        } catch (Exception error) { throw new AssertionError(error); }
        finally {
            context.runOnClient(client -> {
                invoke(DisplayProbe.class, null, "finish", new Class<?>[]{net.minecraft.client.Minecraft.class, String.class}, client, "test_cleanup");
                entities.forEach(entity -> { if (client.level != null) client.level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED); });
                ConfigManager.get().render.debug = debug;
            });
        }
    }
    private static Object active() { return field(DisplayProbe.class, null, "active"); }
    private static Object get(Object object, String name) { return field(object.getClass(), object, name); }
    private static Object field(Class<?> type, Object object, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void set(Object object, String name, Object value) {
        try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static Object invoke(Class<?> type, Object object, String name, Class<?>[] parameters, Object... args) {
        try { var method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method.invoke(object, args); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
