package ru.wilyfox.client.profiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModProfilerReportTest {
    @Test
    void runtimeDiagnosticsAreWrittenWithoutTimingSamples() {
        ModProfiler.ReportSnapshot snapshot = new ModProfiler.ReportSnapshot(
                false,
                0L,
                0L,
                0L,
                1_700_000_000_000L,
                0L,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new ModProfiler.SessionContext(
                        "1.0.0",
                        "1.21.4",
                        "0.19.3",
                        "client",
                        "test",
                        "none",
                        "n/a",
                        "minecraft:overworld",
                        "Player",
                        "60",
                        "960x529"
                ),
                new ModProfiler.RuntimeDiagnostics(
                        12_000L,
                        128L * 1_048_576L,
                        256L * 1_048_576L,
                        512L * 1_048_576L,
                        7L,
                        345L,
                        42,
                        61,
                        200,
                        200
                ),
                List.of(),
                List.of(),
                null
        );

        String report = ModProfiler.getInstance().buildMarkdownReport(snapshot);

        assertTrue(report.contains("## Runtime Diagnostics"));
        assertTrue(report.contains("| Heap used | <code>128.0 MiB</code> |"));
        assertTrue(report.contains("| GC collections | <code>7</code> |"));
        assertTrue(report.contains("| Chat messages retained | <code>42</code> |"));
        assertTrue(report.contains("No profiler timing samples collected"));
    }

    @Test
    void stallStacksAndConnectionTimelineAreWrittenWithoutTimingSamples() {
        long startedAt = 1_700_000_000_000L;
        ModProfiler.ReportSnapshot snapshot = new ModProfiler.ReportSnapshot(
                true,
                startedAt,
                0L,
                2_000L,
                startedAt + 2_000L,
                0L,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new ModProfiler.SessionContext(
                        "1.0.2",
                        "1.21.4",
                        "0.19.3",
                        "client",
                        "test",
                        "none",
                        "n/a",
                        "minecraft:overworld",
                        "Player",
                        "60",
                        "960x529"
                ),
                new ModProfiler.RuntimeDiagnostics(
                        12_000L,
                        128L * 1_048_576L,
                        256L * 1_048_576L,
                        512L * 1_048_576L,
                        7L,
                        345L,
                        42,
                        61,
                        200,
                        200
                ),
                List.of(new ModProfiler.StallView(
                        startedAt + 250L,
                        startedAt + 1_000L,
                        startedAt + 1_750L,
                        1_500L,
                        true,
                        List.of(new ModProfiler.ThreadStackView(
                                "Render thread",
                                "WAITING",
                                List.of("net.minecraft.client.Minecraft.runTick(Minecraft.java:123)")
                        ))
                )),
                List.of(
                        new ModProfiler.TimelineEventView(startedAt, "profiler/start", "minecraft:overworld"),
                        new ModProfiler.TimelineEventView(
                                startedAt + 1_000L,
                                "watchdog/stall-detected",
                                "750 ms without client tick"
                        )
                ),
                null
        );

        String report = ModProfiler.getInstance().buildMarkdownReport(snapshot);

        assertTrue(report.contains("## Client Stall Watchdog"));
        assertTrue(report.contains("Stall 1"));
        assertTrue(report.contains("Render thread"));
        assertTrue(report.contains("Minecraft.runTick"));
        assertTrue(report.contains("## Connection Timeline"));
        assertTrue(report.contains("watchdog/stall-detected"));
        assertTrue(report.contains("<code>1000</code>"));
    }
}
