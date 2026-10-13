package ru.wilyfox.client.profiler;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import ru.wilyfox.client.chat.*;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.menu.ChatPlusImportClientTest;
import ru.wilyfox.client.hud.menu.GoalAndNearbyClientTest;
import ru.wilyfox.client.hud.menu.LevelCalculatorClientTest;
import ru.wilyfox.client.utility.ClickerHoldClientTest;
import java.nio.file.Files;
import java.util.List;

/** Exercise real feature scenarios while recording, then verify the exported timing coverage. */
public final class RecentFeaturesProfilerClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var profiler = ModProfiler.getInstance();
        boolean debug = context.computeOnClient(client -> ConfigManager.get().render.debug);
        try {
            context.runOnClient(client -> { ConfigManager.get().render.debug = false; profiler.stop(); profiler.reset(); profiler.start(); });
            for (var scenario : List.<FabricClientGameTest>of(new ChatDiscordImagesClientTest(),
                    new ClipboardImageClientTest(), new ChatPlusImportClientTest(), new ChatGifClientTest(),
                    new ImageWidgetClientTest(), new ClickerHoldClientTest(), new GoalAndNearbyClientTest(), new LevelCalculatorClientTest())) {
                scenario.runTest(context);
                check(profiler.isEnabled(), "Feature scenario unexpectedly stopped profiling");
            }
            var snapshot = context.computeOnClient(client -> snapshot(profiler));
            var names = snapshot.sections().stream().map(ModProfiler.SectionView::name).toList();
            var required = List.of("ui/FhImageScreen/render", "ui/FhImageAttachScreen/render",
                    "ui/ChatPlusImportScreen/render", "ui/LevelCalculatorScreen/render", "ui/dialog/surface", "ui/dialog/widgets", "hud/panel/geometry",
                    "chat/import/discover", "chat/import/read", "chat/import/parse", "chat/import/append", "chat/import/apply", "chat/import/persist",
                    "chat/imageDecode", "chat/imageUpload", "chat/gifFrameDecode", "chat/gifUpload",
                    "chat/imageAttach/preview/decode", "chat/imageAttach/preview/upload", "chat/imageAttach/prepareUpload",
                    "chat/imageStore/save", "chat/imageStore/read", "hud/ImageWidget/upload", "hud/ImageWidget/gifUpload",
                    "tick/Clicker/hold", "tick/Clicker/release", "hud/LevelGoal/prepare",
                    "hud/NearbyMine/WORMS", "hud/NearbyMine/BARRELS", "hud/NearbyMine/CRYSTALS",
                    "hud/NearbyMine/cache/entities", "hud/NearbyMine/cache/barrels");
            var missing = required.stream().filter(name -> !names.contains(name)).toList();
            check(missing.isEmpty(), "Missing real timing sections: " + missing);
            String report = profiler.buildMarkdownReport(snapshot);
            for (String name : required) check(report.contains(name), "Export omitted timing section: " + name);
            var output = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("froghelper-profiler/recent-features-coverage.md"));
            Files.writeString(output, report);
            context.runOnClient(client -> profiler.stop());
            context.waitTicks(2);
            int count = context.computeOnClient(client -> snapshot(profiler).sections().size());
            context.runOnClient(client -> {
                check(ModProfiler.isNoopScope(profiler.scope("ui/disabled-check")), "Disabled profiling allocated a timing scope");
                try { ChatPlusTabImport.parse("{}"); } catch (java.io.IOException expected) { }
                check(snapshot(profiler).sections().size() == count, "Disabled parsing still collected detailed timings");
            });
            System.out.println("Profiler coverage: all " + required.size() + " required feature timings exported; disabled scopes remain no-op");
        } catch (Exception failure) { throw new AssertionError(failure); }
        finally {
            context.runOnClient(client -> { profiler.stop(); ConfigManager.get().render.debug = debug; });
            try {
                var field = ModProfiler.class.getDeclaredField("crashRecorder"); field.setAccessible(true);
                var recorder = (ProfilerCrashRecorder) field.get(profiler);
                if (recorder != null) recorder.finish().get(15, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception failure) { throw new AssertionError("Profiler fixture did not flush its recorder", failure); }
        }
    }
    private static ModProfiler.ReportSnapshot snapshot(ModProfiler profiler) {
        try {
            var method = ModProfiler.class.getDeclaredMethod("snapshotLocked", ProfilerDiagnostics.FullDiagnostics.class);
            method.setAccessible(true);
            synchronized (profiler) { return (ModProfiler.ReportSnapshot) method.invoke(profiler, new Object[]{null}); }
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
