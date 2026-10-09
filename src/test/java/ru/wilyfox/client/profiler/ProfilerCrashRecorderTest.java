package ru.wilyfox.client.profiler;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ProfilerCrashRecorderTest {
    @TempDir Path temporary;

    @Test
    void stopFlushesLatestReportAndSessionEnd() throws Exception {
        var recorder = recorder(temporary);
        try {
            recorder.checkpoint(() -> "# First report");
            recorder.flushAsync().get(10, TimeUnit.SECONDS);
            recorder.checkpoint(() -> "# Final report");
            recorder.append("client/teleport", "destination", false);
            recorder.finish().get(10, TimeUnit.SECONDS);
            assertTrue(Files.readString(recorder.directory().resolve("report.md")).contains("# Final report"));
            var kinds = kinds(recorder.directory());
            assertTrue(kinds.containsAll(List.of("session/start", "session/stop", "memory", "client/teleport")));
        } finally { recorder.finish().get(10, TimeUnit.SECONDS); }
    }

    @Test
    void queuedSavesAfterFinalCheckpointDoNotTouchClosedFiles() throws Exception {
        var errors = new CopyOnWriteArrayList<String>();
        var recorder = new ProfilerCrashRecorder(temporary, Map.of(), false, errors::add);
        var field = ProfilerCrashRecorder.class.getDeclaredField("writer");
        field.setAccessible(true);
        var writer = (ScheduledExecutorService) field.get(recorder);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            recorder.flushAsync().get(10, TimeUnit.SECONDS);
            writer.execute(() -> {
                entered.countDown();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            // Make the periodic save due before finish queues its immediate save.
            Thread.sleep(1_100);
            recorder.checkpoint(() -> "# Final checkpoint");
            var finish = recorder.finish();
            assertSame(finish, recorder.finish());
            release.countDown();
            finish.get(10, TimeUnit.SECONDS);
            assertTrue(writer.awaitTermination(10, TimeUnit.SECONDS));
            assertTrue(errors.isEmpty(), errors::toString);
            assertTrue(Files.readString(recorder.directory().resolve("report.md")).contains("# Final checkpoint"));
            assertEquals(1L, kinds(recorder.directory()).stream().filter("session/stop"::equals).count());
        } finally {
            release.countDown();
            recorder.finish().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void failedReplacementLeavesPreviousCompleteFile() throws Exception {
        Path report = temporary.resolve("report.md");
        Files.writeString(report, "previous complete file");
        assertThrows(IOException.class, () -> ProfilerCrashRecorder.replaceFile(report, file -> {
            Files.writeString(file, "partial replacement");
            throw new IOException("simulated disk error");
        }));
        assertEquals("previous complete file", Files.readString(report));
        try (var files = Files.list(temporary)) { assertEquals(1, files.count()); }
    }

    @Test
    void ioFailureIsReportedWithoutStoppingTheCaller() throws Exception {
        Path blocked = temporary.resolve("not-a-directory");
        Files.writeString(blocked, "file");
        var errors = new CopyOnWriteArrayList<String>();
        var recorder = new ProfilerCrashRecorder(blocked, Map.of(), false, errors::add);
        assertThrows(ExecutionException.class, () -> recorder.flushAsync().get(10, TimeUnit.SECONDS));
        assertFalse(errors.isEmpty());
        assertTrue(recorder.status().contains("ERROR"));
        assertThrows(ExecutionException.class, () -> recorder.finish().get(10, TimeUnit.SECONDS));
    }

    @Test
    void queueAndPendingCheckpointsStayBoundedWhenIoIsBlocked() throws Exception {
        var recorder = recorder(temporary);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            recorder.checkpoint(() -> {
                entered.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new AssertionError(e); }
                return "old report";
            });
            var firstFlush = recorder.flushAsync();
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            for (int i = 0; i < 2_000; i++) {
                recorder.append("test/event", i, false);
                int index = i;
                recorder.checkpoint(() -> "latest report " + index);
            }
            release.countDown();
            firstFlush.get(10, TimeUnit.SECONDS);
            recorder.finish().get(10, TimeUnit.SECONDS);
            assertTrue(Files.readString(recorder.directory().resolve("report.md")).contains("latest report 1999"));
            var kinds = kinds(recorder.directory());
            assertTrue(kinds.contains("journal/dropped"));
            assertTrue(kinds.stream().filter("test/event"::equals).count() <= 128);
        } finally { release.countDown(); recorder.finish().get(10, TimeUnit.SECONDS); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"halt", "midwrite", "kill"})
    void completedJournalReportAndJfrSurviveAbruptProcessDeath(String mode) throws Exception {
        Path root = temporary.resolve(mode);
        Files.createDirectories(root);
        String separator = System.getProperty("path.separator");
        String classpath = String.join(separator, location(ProfilerCrashProcess.class),
                location(ProfilerCrashRecorder.class), location(Gson.class));
        Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        var process = new ProcessBuilder(java.toString(), "-Xmx128m", "-cp", classpath,
                ProfilerCrashProcess.class.getName(), root.toString(), mode)
                .redirectErrorStream(true).redirectOutput(root.resolve("child-output.txt").toFile()).start();
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
            while (!Files.exists(root.resolve("ready")) && process.isAlive() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(Files.exists(root.resolve("ready")), () -> readOutput(root));
            Path saved = Path.of(Files.readString(root.resolve("ready")));
            if (mode.equals("kill")) process.destroyForcibly();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), () -> readOutput(root));
            if (!mode.equals("kill")) assertEquals(42, process.exitValue(), () -> readOutput(root));
            assertTrue(Files.readString(saved.resolve("report.md")).contains("# Complete checkpoint"));
            assertFalse(Files.readString(saved.resolve("report.md")).contains("partial and invalid"));
            var kinds = kinds(saved);
            assertTrue(kinds.containsAll(List.of("session/start", "memory", "client/teleport")));
            assertFalse(kinds.contains("session/stop"), "A shutdown hook must not be required for persistence");
            try (var recording = new RecordingFile(saved.resolve("profile.jfr"))) {
                assertTrue(recording.hasMoreEvents());
                while (recording.hasMoreEvents()) recording.readEvent();
            }
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); }
        }
    }

    private static ProfilerCrashRecorder recorder(Path root) {
        return new ProfilerCrashRecorder(root, Map.of("test", true), false, error -> fail(error));
    }
    private static String location(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }
    private static List<String> kinds(Path directory) throws IOException {
        return Files.readAllLines(directory.resolve("events.jsonl")).stream().filter(line -> !line.isBlank())
                .map(line -> JsonParser.parseString(line).getAsJsonObject().get("kind").getAsString()).toList();
    }
    private static String readOutput(Path root) {
        try { return Files.readString(root.resolve("child-output.txt")); } catch (IOException e) { return e.toString(); }
    }
}
