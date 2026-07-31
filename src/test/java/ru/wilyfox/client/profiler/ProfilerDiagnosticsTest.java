package ru.wilyfox.client.profiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilerDiagnosticsTest {
    @Test
    void parsesAndSortsHotSpotClassHistogram() {
        String histogram = """
                 num     #instances         #bytes  class name (module)
                -------------------------------------------------------
                   1:            21        3100240  [Ljdk.internal.vm.FillerElement; (java.base@21)
                   2:         32140        2230624  [B (java.base@21)
                   3:         26802         643248  java.lang.String (java.base@21)
                Total         58963        5974112
                """;

        ProfilerDiagnostics.HistogramResult result = ProfilerDiagnostics.parseHistogram(histogram);

        assertTrue(result.error().isEmpty());
        assertEquals(3, result.entries().size());
        assertEquals("[Ljdk.internal.vm.FillerElement; (java.base@21)", result.entries().get(0).className());
        assertEquals(3_100_240L, result.entries().get(0).bytes());
        assertEquals("[B (java.base@21)", result.entries().get(1).className());
    }

    @Test
    void sessionResetKeepsLongRunningProtocolCounters() {
        ModProfiler profiler = ModProfiler.getInstance();
        ModProfiler.LifetimeView before = profiler.lifetimeSnapshot();

        profiler.recordProtocolPayloadReceived(37);
        profiler.reset();

        ModProfiler.LifetimeView after = profiler.lifetimeSnapshot();
        assertEquals(before.protocolPayloadCount() + 1, after.protocolPayloadCount());
        assertEquals(before.protocolPayloadBytes() + 37, after.protocolPayloadBytes());
    }
}
