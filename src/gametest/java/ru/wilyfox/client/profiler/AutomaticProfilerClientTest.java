package ru.wilyfox.client.profiler;

import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordingFile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import ru.wilyfox.client.hud.config.ConfigManager;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.zip.ZipFile;

public final class AutomaticProfilerClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var profiler = ModProfiler.getInstance();
        boolean debug = context.computeOnClient(client -> ConfigManager.get().render.debug);
        try {
            context.runOnClient(client -> { ConfigManager.get().render.debug = false; profiler.stop(); });
            context.waitTicks(2);
            context.runOnClient(client -> {
                profiler.start();
                try (var scope = profiler.scope("test/before-automatic-window")) { }
            });
            long recordings = FlightRecorder.getFlightRecorder().getRecordings().size();
            context.runOnClient(client -> {
                profiler.startAutomaticCapture(14);
                try (var scope = profiler.scope("test/inside-automatic-window")) { }
            });
            expect(FlightRecorder.getFlightRecorder().getRecordings().size() <= recordings,"Automatic capture created a second JFR recorder");
            context.waitTicks(10);
            // End from a timer thread; this must not read client/world objects off-thread.
            var capture = CompletableFuture.supplyAsync(profiler::finishAutomaticCapture).thenCompose(f -> f).get(20,TimeUnit.SECONDS);
            expect(profiler.isEnabled() && !capture.ownsDirectory(),"Automatic capture stopped the manual session");
            expect(capture.markdown().contains("test/inside-automatic-window") && !capture.markdown().contains("test/before-automatic-window"),"Report included timings outside the capture window");
            try (var recording = new RecordingFile(capture.directory().resolve("profile.jfr"))) { expect(recording.hasMoreEvents(),"Manual overlap has no JFR events"); }
            context.runOnClient(client -> profiler.stop());
            var previous = recorder(profiler); previous.finish().get(20,TimeUnit.SECONDS);
            context.runOnClient(client -> { ConfigManager.get().render.debug = true; });
            context.waitTicks(2);
            var directory = context.computeOnClient(client -> client.gameDirectory.toPath());
            var outbox = new DiagnosticOutbox(directory,"1.1.4","26.2");
            var service = new AutomaticDiagnostics();
            var field = AutomaticDiagnostics.class.getDeclaredField("outbox"); field.setAccessible(true); field.set(service,outbox);
            java.nio.file.Path raw;
            try {
                context.runOnClient(client -> service.considerCapture(8,false,0));
                expect(!profiler.isEnabled(),"Inactive window triggered automatic recording");
                context.runOnClient(client -> service.considerCapture(8,true,0));
                expect(!profiler.isEnabled(),"A single low FPS sample triggered recording");
                context.runOnClient(client -> service.considerCapture(8,true,4_999));
                expect(!profiler.isEnabled(),"Recording started before five seconds of low FPS");
                context.runOnClient(client -> service.considerCapture(8,true,5_000));
                expect(profiler.isEnabled(),"Five seconds of low FPS did not trigger recording");
                raw = recorder(profiler).directory();
                long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
                var activeField = AutomaticDiagnostics.class.getDeclaredField("capturing"); activeField.setAccessible(true);
                var active = (java.util.concurrent.atomic.AtomicBoolean)activeField.get(service);
                while (active.get() && System.nanoTime()<deadline) context.waitTicks(2);
            } finally {
                var worker = AutomaticDiagnostics.class.getDeclaredField("worker"); worker.setAccessible(true);
                ((ScheduledExecutorService)worker.get(service)).shutdown();
            }
            expect(!profiler.isEnabled(),"15-second timer did not stop its recorder");
            var report = outbox.next(System.currentTimeMillis());
            expect(report!=null && report.manifest().durationMs()>=15000 && report.manifest().durationMs()<20000,"Automatic timer recorded an incorrect duration");
            expect(report != null && report.manifest().kind().equals("low-fps"),"Profile was not queued");
            try (var zip = new ZipFile(report.path().toFile())) { expect(zip.getEntry("profile.jfr") != null && zip.getEntry("report.md") != null,"Archive missed JFR/report"); }
            expect(!Files.exists(raw),"Packaged automatic raw files were not cleaned up");
            expect(Files.exists(previous.directory().resolve("report.md")),"Automatic cleanup removed a manual profile");
            outbox.accepted(report);
        } catch (Exception failure) { throw new AssertionError(failure); }
        finally {
            context.runOnClient(client -> { profiler.stop(); ConfigManager.get().render.debug = debug; });
            context.waitTicks(2);
        }
    }
    private static ProfilerCrashRecorder recorder(ModProfiler profiler) {
        try { var field = ModProfiler.class.getDeclaredField("crashRecorder"); field.setAccessible(true); return (ProfilerCrashRecorder)field.get(profiler); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void expect(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
}
