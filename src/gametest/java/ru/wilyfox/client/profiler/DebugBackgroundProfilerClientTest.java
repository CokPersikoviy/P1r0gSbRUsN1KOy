package ru.wilyfox.client.profiler;

import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordingFile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.HudConfigCodec;
import ru.wilyfox.client.hud.config.HudConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public final class DebugBackgroundProfilerClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        HudConfig original = context.computeOnClient(client -> HudConfigCodec.createGson().fromJson(HudConfigCodec.createGson().toJson(ConfigManager.get()), HudConfig.class));
        var profiler = ModProfiler.getInstance();
        try {
            context.runOnClient(client -> ConfigManager.get().render.debug = false);
            context.waitTicks(2);
            context.runOnClient(client -> profiler.stop());
            var previousRecorder = context.computeOnClient(client -> recorder(profiler));
            if (previousRecorder != null) previousRecorder.finish().get(15, TimeUnit.SECONDS);
            Path root = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("froghelper-profiler"));
            var originalFiles = files(root);
            var originalJfr = FlightRecorder.getFlightRecorder().getRecordings().stream().map(r -> r.getId()).toList();
            long samples = context.computeOnClient(client -> countSamples(profiler));
            context.runOnClient(client -> forceNextSample(profiler));
            context.waitTicks(3);
            expect(context.computeOnClient(client -> countSamples(profiler)) == samples && !profiler.isEnabled(), "Disabled Debug still collects background samples");

            // Debug starts only the old minimal memory sampling, including in the main menu.
            context.runOnClient(client -> ConfigManager.get().render.debug = true);
            context.waitTicks(3);
            expect(!profiler.isEnabled() && context.computeOnClient(client -> recorder(profiler)) == previousRecorder,
                    "Debug started detailed profiling or a disk recorder");
            expect(context.computeOnClient(client -> countSamples(profiler)) > samples, "Debug did not collect the initial memory sample");
            long initialSamples = context.computeOnClient(client -> countSamples(profiler));
            context.waitTicks(210); // Verify that memory sampling continues after the ten-second interval.
            expect(context.computeOnClient(client -> countSamples(profiler)) > initialSamples, "Debug memory sampling was not periodic");
            expect(files(root).equals(originalFiles), "Debug created or updated profiler files without a command");
            expect(FlightRecorder.getFlightRecorder().getRecordings().stream().map(r -> r.getId()).toList().equals(originalJfr),
                    "Debug started a JFR recording");

            context.runOnClient(client -> {
                try (var ignored = profiler.scope("test/debug-no-detailed-timing")) { }
                expect(snapshot(profiler).sections().stream().noneMatch(s -> s.name().equals("test/debug-no-detailed-timing")),
                        "Debug memory sampling enabled detailed timings");
                profiler.start(); // Only an explicit command's action starts persistence.
                expect(profiler.isEnabled() && recorder(profiler) != previousRecorder, "Manual start did not create a disk recorder");
            });
            var activeRecorder = context.computeOnClient(client -> recorder(profiler));
            context.runOnClient(client -> ConfigManager.get().render.debug = false);
            context.waitTicks(3);
            expect(profiler.isEnabled(), "Disabling Debug stopped the explicit manual session");
            context.runOnClient(client -> ConfigManager.get().render.debug = true);
            context.waitTicks(3);
            try (var world = context.worldBuilder().create()) {
                context.runOnClient(client -> {
                    var renderer = Client.getInstance().getHudRenderer();
                    renderer.setSettings(false);
                    client.gui.setScreen(new HudEditingScreen(renderer));
                    renderer.setEditing(true);
                    var widget = renderer.getWidgets().stream().filter(w -> w.getClass().getSimpleName().equals("BlocksPerSecondWidget")).findFirst().orElseThrow();
                    if (renderer.isWidgetAdded(widget)) renderer.removeWidget(widget);
                    renderer.addWidget(widget);
                    renderer.render(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO);
                    renderer.renderSettingsOverlay(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO);
                    renderer.removeWidget(widget);
                    renderer.render(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), DeltaTracker.ZERO);
                    var minimal = ProfilerDiagnostics.captureSample(client, false).world();
                    expect(minimal.topEntityTypes().isEmpty() && minimal.topBlockEntityTypes().isEmpty() && minimal.blockEntities() == -1,
                            "Minimal sampling scanned world object types");
                    expect(minimal.loadedChunks() >= 0 && minimal.entities() >= 0, "Minimal sampling lost existing world counts");
                    client.gui.setScreen(null); renderer.setEditing(false);
                });
                context.waitTicks(110);
            }
            activeRecorder.flushAsync().get(15, TimeUnit.SECONDS);
            String report = Files.readString(activeRecorder.directory().resolve("report.md"));
            expect(report.contains("hud/animation/") && report.contains("ui/widgetSidebar/render") && report.contains("hud/layout/apply"),
                    "Manual report missed new HUD timing sections");
            expect(report.contains("widget-add") && report.contains("widget-remove"), "Widget actions missing from timeline");
            try (var recording = new RecordingFile(activeRecorder.directory().resolve("profile.jfr"))) {
                expect(recording.hasMoreEvents(), "Manual recorder did not publish a readable JFR");
            }
            context.runOnClient(client -> profiler.stop());
            activeRecorder.finish().get(15, TimeUnit.SECONDS);
            expect(!profiler.isEnabled(), "Manual stop did not stop disk recording while Debug was enabled");
            expect(Files.readString(activeRecorder.directory().resolve("events.jsonl")).contains("session/stop"), "Manual stop did not flush the final state");
            var stoppedFiles = files(root);
            samples = context.computeOnClient(client -> countSamples(profiler));
            context.runOnClient(client -> forceNextSample(profiler));
            context.waitTicks(3);
            expect(context.computeOnClient(client -> countSamples(profiler)) > samples, "Manual stop stopped Debug memory sampling");
            expect(files(root).equals(stoppedFiles), "Memory sampling still wrote to a stopped manual recorder");

            context.runOnClient(client -> ConfigManager.get().render.debug = false);
            context.waitTicks(3);
            samples = context.computeOnClient(client -> countSamples(profiler));
            var disabledSnapshot = context.computeOnClient(client -> snapshot(profiler));
            context.runOnClient(client -> {
                forceNextSample(profiler);
                profiler.recordClientEvent("disabled-test", "ignored");
                profiler.recordProtocolPayloadReceived(37);
            });
            context.waitTicks(3);
            var after = context.computeOnClient(client -> snapshot(profiler));
            expect(context.computeOnClient(client -> countSamples(profiler)) == samples && after.lifetime().equals(disabledSnapshot.lifetime())
                    && after.lifetimeTimeline().equals(disabledSnapshot.lifetimeTimeline()), "Disabled profiling still collected diagnostics");
            expect(files(root).equals(stoppedFiles), "Disabled profiling still wrote to disk");
        } catch (Exception failure) { throw new AssertionError(failure); }
        finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); Client.getInstance().getHudRenderer().setEditing(false);
                ConfigManager.get().render.debug = false;
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                profiler.stop();
                try { var config = ConfigManager.class.getDeclaredField("CONFIG"); config.setAccessible(true); config.set(null, original); }
                catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                ConfigManager.layoutChanged(); ConfigManager.save(); Client.getInstance().getHudRenderer().refreshLayout();
            });
        }
    }
    private static Map<String, String> files(Path root) throws Exception {
        var files = new TreeMap<String, String>();
        if (Files.exists(root)) try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList())
                files.put(root.relativize(path).toString(), Files.size(path) + ":" + Files.getLastModifiedTime(path));
        }
        return files;
    }
    private static void forceNextSample(ModProfiler profiler) {
        try { var field = ModProfiler.class.getDeclaredField("lastPersistentSampleAtMs"); field.setAccessible(true); synchronized (profiler) { field.setLong(profiler, 0L); } }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static long countSamples(ModProfiler profiler) {
        try { var field = ModProfiler.class.getDeclaredField("persistentSamples"); field.setAccessible(true); synchronized (profiler) { return ((java.util.Deque<?>) field.get(profiler)).size(); } }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static ModProfiler.ReportSnapshot snapshot(ModProfiler profiler) {
        try {
            var method = ModProfiler.class.getDeclaredMethod("snapshotLocked", ProfilerDiagnostics.FullDiagnostics.class);
            method.setAccessible(true);
            synchronized (profiler) { return (ModProfiler.ReportSnapshot) method.invoke(profiler, new Object[]{null}); }
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static ProfilerCrashRecorder recorder(ModProfiler profiler) {
        try { var field = ModProfiler.class.getDeclaredField("crashRecorder"); field.setAccessible(true); return (ProfilerCrashRecorder) field.get(profiler); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void expect(boolean result, String detail) { if (!result) throw new AssertionError(detail); }
}
