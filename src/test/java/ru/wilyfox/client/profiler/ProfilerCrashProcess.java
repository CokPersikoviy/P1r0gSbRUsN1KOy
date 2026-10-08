package ru.wilyfox.client.profiler;

import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Only on the test classpath. Intentionally exits without running shutdown hooks. */
public final class ProfilerCrashProcess {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        var recorder = new ProfilerCrashRecorder(root, Map.of("test", "abrupt-exit"), true, System.err::println);
        recorder.checkpoint(() -> "# Complete checkpoint\n\nBefore simulated crash.\n");
        recorder.append("client/teleport", Map.of("dimension", "test:destination"), true);
        recorder.flushAsync().get(20, TimeUnit.SECONDS);
        Files.writeString(root.resolve("ready.tmp"), recorder.directory().toString());
        Files.move(root.resolve("ready.tmp"), root.resolve("ready"), StandardCopyOption.ATOMIC_MOVE);
        switch (args[1]) {
            case "halt" -> Runtime.getRuntime().halt(42);
            case "midwrite" -> ProfilerCrashRecorder.replaceFile(recorder.directory().resolve("report.md"), temporary -> {
                Files.writeString(temporary, "partial and invalid checkpoint");
                Runtime.getRuntime().halt(42);
            });
            case "kill" -> Thread.sleep(60_000);
            default -> throw new IllegalArgumentException(args[1]);
        }
    }
}
