package ru.wilyfox.client.profiler;

import com.google.gson.JsonParser;
import jdk.jfr.consumer.RecordingFile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ProfilerCrashSaveClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var reference = new AtomicReference<ProfilerCrashRecorder>();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var profiler = ModProfiler.getInstance();
                profiler.reset();
                profiler.start();
                reference.set(recorder(profiler));
                profiler.recordClientEvent("teleport", "test:crash-save-world");
                try (var ignored = profiler.scope("test/crash-save-timing")) { }
            });
            var recorder = reference.get();
            expect(recorder != null, "Profiler did not create its automatic recorder");
            context.waitTicks(105); // Exercise the actual five-second client checkpoint hook.
            try {
                recorder.flushAsync().get(15, TimeUnit.SECONDS);
                String report = Files.readString(recorder.directory().resolve("report.md"));
                expect(report.contains("test/crash-save-timing"), "Automatic report missed collected timing samples");
                expect(report.contains("test:crash-save-world"), "Automatic report missed the transition event");
                var records = Files.readAllLines(recorder.directory().resolve("events.jsonl")).stream()
                        .map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
                expect(records.stream().anyMatch(record -> record.get("kind").getAsString().equals("memory")),
                        "Background memory journal is missing");
                try (var jfr = new RecordingFile(recorder.directory().resolve("profile.jfr"))) {
                    expect(jfr.hasMoreEvents(), "Automatic JFR has no events");
                }
                context.runOnClient(client -> {
                    try (var recording = new jdk.jfr.Recording()) {
                        recording.enable("jdk.ExecuteVMOperation").withThreshold(java.time.Duration.ZERO);
                        recording.start();
                        Path dump = ModProfiler.getInstance().writeMarkdownReport(recorder.directory().resolve("manual"));
                        expect(Files.readString(dump).contains("Histogram skipped"), "Regular dump collected a heap histogram");
                        recording.stop();
                        Path operations = recorder.directory().resolve("dump-operations.jfr");
                        recording.dump(operations);
                        try (var events = new RecordingFile(operations)) {
                            while (events.hasMoreEvents()) {
                                var event = events.readEvent();
                                if (event.getEventType().getName().equals("jdk.ExecuteVMOperation")) {
                                    expect(!event.getString("operation").equals("GC_HeapInspection"),
                                            "Regular dump paused the JVM for a heap inspection");
                                }
                            }
                        }
                    } catch (Exception exception) { throw new AssertionError(exception); }
                });
                Path finalDirectory = recorder.directory();
                context.runOnClient(client -> ModProfiler.getInstance().stop());
                recorder.finish().get(15, TimeUnit.SECONDS);
                expect(Files.readString(finalDirectory.resolve("report.md")).contains("<code>false</code>"),
                        "Stop did not save the final disabled state");
                expect(Files.readString(finalDirectory.resolve("events.jsonl")).contains("session/stop"),
                        "Stop did not flush the journal");
            } catch (Exception exception) { throw new AssertionError(exception); }
            finally { context.runOnClient(client -> ModProfiler.getInstance().stop()); }
        }
    }

    private static ProfilerCrashRecorder recorder(ModProfiler profiler) {
        try {
            var field = ModProfiler.class.getDeclaredField("crashRecorder");
            field.setAccessible(true);
            return (ProfilerCrashRecorder) field.get(profiler);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
