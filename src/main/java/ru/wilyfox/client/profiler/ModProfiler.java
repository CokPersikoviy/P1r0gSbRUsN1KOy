package ru.wilyfox.client.profiler;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import ru.wilyfox.client.chat.ChatTabManager;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static ru.wilyfox.FrogHelper.MOD_ID;

public final class ModProfiler {
    private static final ModProfiler INSTANCE = new ModProfiler();
    private static final Scope NOOP_SCOPE = () -> { };
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());
    private static final int CALL_TREE_CHILD_LIMIT = 12;
    private static final int CALL_TREE_DEPTH_LIMIT = 6;
    private static final int SAMPLE_HISTORY_LIMIT = 256;
    private static final int SAMPLE_REPORT_LIMIT = 25;
    private static final long WATCHDOG_POLL_MILLIS = 250L;
    private static final long STALL_THRESHOLD_NANOS = 750_000_000L;
    private static final int STALL_HISTORY_LIMIT = 12;
    private static final int TIMELINE_HISTORY_LIMIT = 128;
    private static final int STACK_TRACE_LIMIT = 48;
    private static final int DIAGNOSTIC_THREAD_LIMIT = 8;

    private final Map<String, SectionStats> statsBySection = new LinkedHashMap<>();
    private final Map<String, CounterStats> countersByName = new LinkedHashMap<>();
    private final Map<String, CallTreeNodeStats> rootNodes = new LinkedHashMap<>();
    private final Deque<ScopeSample> recentSamples = new ArrayDeque<>();
    private final Deque<StallCapture> stallCaptures = new ArrayDeque<>();
    private final Deque<TimelineEvent> connectionTimeline = new ArrayDeque<>();
    private final ThreadLocal<Deque<ActiveScope>> activeScopes = ThreadLocal.withInitial(ArrayDeque::new);
    private final AtomicBoolean diagnosticsRegistered = new AtomicBoolean();
    private boolean enabled;
    private long sessionStartedAt;
    private long sessionStoppedAt;
    private long lastClientHeartbeatNanos;
    private long lastClientHeartbeatAtMs;
    private long lastProtocolPayloadAtMs;
    private Thread clientThread;
    private StallCapture activeStall;
    private String observedDimension = "";

    private ModProfiler() {
    }

    public static ModProfiler getInstance() {
        return INSTANCE;
    }

    public void registerDiagnostics() {
        if (!diagnosticsRegistered.compareAndSet(false, true)) {
            return;
        }

        ClientTickEvents.START_CLIENT_TICK.register(this::heartbeat);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            recordTimelineEvent("connection/join", handler.getLocalGameProfile().getName());
            observeDimension(client, true);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            recordTimelineEvent("connection/disconnect", currentDimension(client));
            synchronized (this) {
                observedDimension = "";
                lastProtocolPayloadAtMs = 0L;
            }
        });

        Thread watchdog = new Thread(this::runWatchdog, "froghelper-profiler-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    public synchronized void start() {
        enabled = true;
        sessionStartedAt = System.currentTimeMillis();
        sessionStoppedAt = 0L;
        lastClientHeartbeatNanos = System.nanoTime();
        lastClientHeartbeatAtMs = sessionStartedAt;
        lastProtocolPayloadAtMs = 0L;
        activeStall = null;
        recordTimelineEventLocked("profiler/start", currentDimension(Minecraft.getInstance()));
    }

    public synchronized void stop() {
        long now = System.currentTimeMillis();
        if (activeStall != null) {
            activeStall.recoveredAtMs = now;
            activeStall = null;
        }
        if (enabled) {
            recordTimelineEventLocked("profiler/stop", currentDimension(Minecraft.getInstance()));
        }
        enabled = false;
        sessionStoppedAt = now;
    }

    public synchronized void reset() {
        statsBySection.clear();
        countersByName.clear();
        rootNodes.clear();
        recentSamples.clear();
        stallCaptures.clear();
        connectionTimeline.clear();
        sessionStartedAt = enabled ? System.currentTimeMillis() : 0L;
        sessionStoppedAt = 0L;
        lastClientHeartbeatNanos = enabled ? System.nanoTime() : 0L;
        lastClientHeartbeatAtMs = enabled ? sessionStartedAt : 0L;
        lastProtocolPayloadAtMs = 0L;
        activeStall = null;
        observedDimension = "";
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public void recordProtocolHandshake(String phase) {
        recordTimelineEvent("protocol/handshake/" + safeTimelineValue(phase), currentDimension(Minecraft.getInstance()));
    }

    public synchronized void recordProtocolPayloadReceived(int payloadBytes) {
        if (!enabled) {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastProtocolPayloadAtMs <= 0L || now - lastProtocolPayloadAtMs >= 1_000L) {
            recordTimelineEventLocked(
                    "protocol/payload-batch",
                    payloadBytes + " bytes on " + Thread.currentThread().getName()
            );
        }
        lastProtocolPayloadAtMs = now;
    }

    private void heartbeat(Minecraft minecraft) {
        long nowNanos = System.nanoTime();
        long nowMs = System.currentTimeMillis();
        synchronized (this) {
            clientThread = Thread.currentThread();
            if (enabled && activeStall != null) {
                activeStall.recoveredAtMs = nowMs;
                recordTimelineEventLocked(
                        "watchdog/recovered",
                        Math.max(0L, nowMs - activeStall.startedAtMs) + " ms"
                );
                activeStall = null;
            }
            lastClientHeartbeatNanos = nowNanos;
            lastClientHeartbeatAtMs = nowMs;
        }
        observeDimension(minecraft, false);
    }

    private void observeDimension(Minecraft minecraft, boolean force) {
        String dimension = currentDimension(minecraft);
        synchronized (this) {
            if (!enabled || !force && dimension.equals(observedDimension)) {
                return;
            }

            String previous = observedDimension;
            observedDimension = dimension;
            lastProtocolPayloadAtMs = 0L;
            recordTimelineEventLocked(
                    previous.isBlank() ? "dimension/observed" : "dimension/change",
                    previous.isBlank() || previous.equals("n/a")
                            ? dimension
                            : previous + " -> " + dimension
            );
        }
    }

    private void runWatchdog() {
        while (true) {
            try {
                Thread.sleep(WATCHDOG_POLL_MILLIS);
                inspectForClientStall();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable ignored) {
                // Diagnostics must never destabilize the game client.
            }
        }
    }

    private void inspectForClientStall() {
        StallCapture capture;
        Thread targetThread;
        synchronized (this) {
            if (!enabled
                    || lastClientHeartbeatNanos <= 0L
                    || activeStall != null
                    || System.nanoTime() - lastClientHeartbeatNanos < STALL_THRESHOLD_NANOS) {
                return;
            }

            long detectedAtMs = System.currentTimeMillis();
            capture = new StallCapture(lastClientHeartbeatAtMs, detectedAtMs);
            activeStall = capture;
            stallCaptures.addLast(capture);
            while (stallCaptures.size() > STALL_HISTORY_LIMIT) {
                stallCaptures.removeFirst();
            }
            targetThread = clientThread;
            recordTimelineEventLocked(
                    "watchdog/stall-detected",
                    Math.max(0L, detectedAtMs - lastClientHeartbeatAtMs) + " ms without client tick"
            );
        }

        List<ThreadStackView> stacks = captureRelevantThreadStacks(targetThread);
        synchronized (this) {
            capture.threadStacks = stacks;
        }
    }

    private List<ThreadStackView> captureRelevantThreadStacks(Thread targetThread) {
        Map<Thread, StackTraceElement[]> allStacks = Thread.getAllStackTraces();
        List<ThreadStackView> captured = new ArrayList<>();
        if (targetThread != null) {
            addThreadStack(captured, targetThread, allStacks.get(targetThread));
        }

        int diagnosticThreads = 0;
        List<Thread> threads = allStacks.keySet().stream()
                .sorted(Comparator.comparingInt(this::diagnosticThreadPriority).thenComparing(Thread::getName))
                .toList();
        for (Thread thread : threads) {
            if (thread == targetThread || !isRelevantDiagnosticThread(thread.getName())) {
                continue;
            }
            addThreadStack(captured, thread, allStacks.get(thread));
            if (++diagnosticThreads >= DIAGNOSTIC_THREAD_LIMIT) {
                break;
            }
        }
        return List.copyOf(captured);
    }

    private void addThreadStack(
            List<ThreadStackView> target,
            Thread thread,
            StackTraceElement[] stackTrace
    ) {
        if (thread == null) {
            return;
        }
        List<String> frames = stackTrace == null
                ? List.of()
                : java.util.Arrays.stream(stackTrace)
                        .limit(STACK_TRACE_LIMIT)
                        .map(StackTraceElement::toString)
                        .toList();
        target.add(new ThreadStackView(thread.getName(), thread.getState().name(), frames));
    }

    private boolean isRelevantDiagnosticThread(String threadName) {
        String normalized = threadName == null ? "" : threadName.toLowerCase(Locale.ROOT);
        return normalized.contains("netty")
                || normalized.contains("client io")
                || normalized.contains("server connector")
                || normalized.contains("network")
                || normalized.contains("resource")
                || normalized.contains("download")
                || normalized.contains("worker-main")
                || normalized.contains("forkjoinpool");
    }

    private int diagnosticThreadPriority(Thread thread) {
        String normalized = thread.getName().toLowerCase(Locale.ROOT);
        return normalized.contains("netty")
                || normalized.contains("client io")
                || normalized.contains("server connector")
                || normalized.contains("network")
                ? 0
                : 1;
    }

    private void recordTimelineEvent(String event, String detail) {
        synchronized (this) {
            if (enabled) {
                recordTimelineEventLocked(event, detail);
            }
        }
    }

    private void recordTimelineEventLocked(String event, String detail) {
        connectionTimeline.addLast(new TimelineEvent(
                System.currentTimeMillis(),
                safeTimelineValue(event),
                safeTimelineValue(detail)
        ));
        while (connectionTimeline.size() > TIMELINE_HISTORY_LIMIT) {
            connectionTimeline.removeFirst();
        }
    }

    private static String currentDimension(Minecraft minecraft) {
        return minecraft != null && minecraft.level != null
                ? minecraft.level.dimension().location().toString()
                : "n/a";
    }

    private static String safeTimelineValue(String value) {
        return value == null || value.isBlank() ? "n/a" : value;
    }

    public Scope scope(String section) {
        if (!isEnabled() || section == null || section.isBlank()) {
            return NOOP_SCOPE;
        }

        Deque<ActiveScope> stack = activeScopes.get();
        ActiveScope scope = new ActiveScope(section, System.nanoTime(), stack.peekLast());
        stack.addLast(scope);
        return () -> closeScope(scope, stack);
    }

    public void incrementCounter(String counter) {
        incrementCounter(counter, 1L);
    }

    public synchronized void incrementCounter(String counter, long delta) {
        if (!enabled || counter == null || counter.isBlank() || delta == 0L) {
            return;
        }

        CounterStats stats = countersByName.computeIfAbsent(counter, ignored -> new CounterStats());
        stats.events++;
        stats.total += delta;
        stats.maxDelta = Math.max(stats.maxDelta, delta);
    }

    public synchronized List<String> buildReportLines() {
        return buildReportLines(null);
    }

    public synchronized List<String> buildReportLines(String prefixFilter) {
        ReportSnapshot snapshot = filterSnapshot(snapshot(), prefixFilter);
        RuntimeDiagnostics runtime = snapshot.runtimeDiagnostics();
        if (snapshot.sections().isEmpty()) {
            return List.of(
                    "No profiler timing samples collected.",
                    formatRuntimeLine(runtime),
                    formatWatchdogLine(snapshot.stalls())
            );
        }

        List<String> lines = new ArrayList<>();
        lines.add("Enabled: " + snapshot.enabled() + ", sections: " + snapshot.sections().size() + ", sessionMs: " + snapshot.sessionDurationMs());
        lines.add(formatRuntimeLine(runtime));
        lines.add(formatWatchdogLine(snapshot.stalls()));
        if (snapshot.focusPrefix() != null) {
            lines.add("Focus prefix: " + snapshot.focusPrefix());
        }
        lines.add("Top sections by total time:");

        snapshot.sections().stream()
                .sorted(Comparator.comparingLong(SectionView::totalNanos).reversed())
                .limit(20)
                .forEach(section -> lines.add(String.format(
                        Locale.ROOT,
                        "%s -> calls=%d total=%.3fms self=%.3fms avg=%.3fms max=%.3fms share=%.1f%%",
                        section.name(),
                        section.calls(),
                        nanosToMillis(section.totalNanos()),
                        nanosToMillis(section.selfNanos()),
                        nanosToMillis(section.avgNanos()),
                        nanosToMillis(section.maxNanos()),
                        section.sharePercent()
                )));

        if (!snapshot.counters().isEmpty()) {
            lines.add("Top counters:");
            snapshot.counters().stream()
                    .sorted(Comparator.comparingLong(CounterView::total).reversed())
                    .limit(10)
                    .forEach(counter -> lines.add(String.format(
                            Locale.ROOT,
                            "%s -> events=%d total=%d avg=%.2f max=%d",
                            counter.name(),
                            counter.events(),
                            counter.total(),
                            counter.avg(),
                            counter.maxDelta()
                    )));
        }

        return lines;
    }

    public synchronized String buildStatusLine() {
        return "Profiler " + (enabled ? "enabled" : "disabled")
                + ", sections=" + statsBySection.size()
                + ", counters=" + countersByName.size()
                + ", stalls=" + stallCaptures.size()
                + ", sessionMs=" + sessionDurationMs();
    }

    public synchronized Path writeMarkdownReport(Path directory) throws IOException {
        return writeMarkdownReport(directory, null);
    }

    public synchronized Path writeMarkdownReport(Path directory, String prefixFilter) throws IOException {
        ReportSnapshot snapshot = filterSnapshot(snapshot(), prefixFilter);
        String timestamp = FILE_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(snapshot.generatedAtMs()));
        String baseName = snapshot.focusPrefix() == null
                ? "fhprof-" + timestamp
                : "fhprof-" + sanitizeFileComponent(snapshot.focusPrefix()) + "-" + timestamp;
        Path output = directory.resolve(baseName + ".md");
        Files.createDirectories(directory);
        Files.writeString(output, buildMarkdownReport(snapshot), StandardCharsets.UTF_8);
        return output.toAbsolutePath().normalize();
    }

    private synchronized ReportSnapshot snapshot() {
        long measuredNanos = statsBySection.values().stream().mapToLong(stat -> stat.totalNanos).sum();
        List<SectionView> sections = new ArrayList<>(statsBySection.size());
        for (Map.Entry<String, SectionStats> entry : statsBySection.entrySet()) {
            SectionStats stat = entry.getValue();
            long avgNanos = stat.calls <= 0L ? 0L : stat.totalNanos / stat.calls;
            long avgSelfNanos = stat.calls <= 0L ? 0L : stat.selfNanos / stat.calls;
            double sharePercent = measuredNanos <= 0L ? 0.0 : stat.totalNanos * 100.0 / measuredNanos;
            sections.add(new SectionView(entry.getKey(), stat.calls, stat.totalNanos, stat.selfNanos, avgNanos, avgSelfNanos, stat.maxNanos, sharePercent));
        }

        List<CounterView> counters = new ArrayList<>(countersByName.size());
        for (Map.Entry<String, CounterStats> entry : countersByName.entrySet()) {
            CounterStats stat = entry.getValue();
            double avg = stat.events <= 0L ? 0.0 : (double) stat.total / stat.events;
            counters.add(new CounterView(entry.getKey(), stat.events, stat.total, stat.maxDelta, avg));
        }

        List<CallTreeNodeView> callTreeRoots = new ArrayList<>(rootNodes.size());
        for (Map.Entry<String, CallTreeNodeStats> entry : rootNodes.entrySet()) {
            callTreeRoots.add(toCallTreeView(entry.getKey(), entry.getValue()));
        }

        List<ScopeSampleView> samples = recentSamples.stream()
                .map(sample -> new ScopeSampleView(
                        sample.section,
                        sample.startedAtMs,
                        sample.endedAtMs,
                        sample.totalNanos,
                        sample.selfNanos,
                        sample.threadName
                ))
                .toList();

        long snapshotAtMs = System.currentTimeMillis();
        List<StallView> stalls = stallCaptures.stream()
                .map(capture -> capture.toView(snapshotAtMs))
                .toList();
        List<TimelineEventView> timeline = connectionTimeline.stream()
                .map(event -> new TimelineEventView(event.timestampMs, event.event, event.detail))
                .toList();

        return new ReportSnapshot(
                enabled,
                sessionStartedAt,
                sessionStoppedAt,
                sessionDurationMs(),
                snapshotAtMs,
                measuredNanos,
                sections,
                counters,
                callTreeRoots,
                samples,
                SessionContext.capture(),
                RuntimeDiagnostics.capture(),
                stalls,
                timeline,
                null
        );
    }

    private void closeScope(ActiveScope scope, Deque<ActiveScope> stack) {
        ActiveScope closedScope = stack.pollLast();
        if (closedScope != scope) {
            stack.remove(scope);
            return;
        }

        long elapsedNanos = Math.max(0L, System.nanoTime() - scope.startedAtNanos);
        long selfNanos = Math.max(0L, elapsedNanos - scope.childNanos);
        if (scope.parent != null) {
            scope.parent.childNanos += elapsedNanos;
        }
        record(scope.section, scope.parent, elapsedNanos, selfNanos);
        if (stack.isEmpty() && !enabled) {
            activeScopes.remove();
        }
    }

    private synchronized void record(String section, ActiveScope parentScope, long elapsedNanos, long selfNanos) {
        if (!enabled) {
            return;
        }

        SectionStats stats = statsBySection.computeIfAbsent(section, ignored -> new SectionStats());
        stats.calls++;
        stats.totalNanos += elapsedNanos;
        stats.selfNanos += selfNanos;
        stats.maxNanos = Math.max(stats.maxNanos, elapsedNanos);

        Map<String, CallTreeNodeStats> targetMap = rootNodes;
        if (parentScope != null) {
            List<ActiveScope> ancestors = new ArrayList<>();
            for (ActiveScope current = parentScope; current != null; current = current.parent) {
                ancestors.add(current);
            }
            for (int i = ancestors.size() - 1; i >= 0; i--) {
                ActiveScope ancestor = ancestors.get(i);
                CallTreeNodeStats ancestorNode = targetMap.computeIfAbsent(ancestor.section, ignored -> new CallTreeNodeStats());
                targetMap = ancestorNode.children;
            }
        }
        CallTreeNodeStats node = targetMap.computeIfAbsent(section, ignored -> new CallTreeNodeStats());
        node.calls++;
        node.totalNanos += elapsedNanos;
        node.selfNanos += selfNanos;
        node.maxNanos = Math.max(node.maxNanos, elapsedNanos);

        if (shouldCaptureSample(section, parentScope)) {
            recentSamples.addLast(new ScopeSample(
                    section,
                    elapsedNanos,
                    selfNanos,
                    System.currentTimeMillis() - nanosToMillisRounded(elapsedNanos),
                    System.currentTimeMillis(),
                    Thread.currentThread().getName()
            ));
            while (recentSamples.size() > SAMPLE_HISTORY_LIMIT) {
                recentSamples.removeFirst();
            }
        }
    }

    private long sessionDurationMs() {
        if (sessionStartedAt <= 0L) {
            return 0L;
        }

        long end = enabled ? System.currentTimeMillis() : (sessionStoppedAt > 0L ? sessionStoppedAt : sessionStartedAt);
        return Math.max(0L, end - sessionStartedAt);
    }

    String buildMarkdownReport(ReportSnapshot snapshot) {
        if (snapshot.sections().isEmpty()) {
            StringBuilder markdown = new StringBuilder(1024);
            appendReportHeader(markdown, snapshot);
            appendSessionContext(markdown, snapshot.context());
            appendRuntimeDiagnostics(markdown, snapshot.runtimeDiagnostics());
            appendStallWatchdog(markdown, snapshot.stalls());
            appendConnectionTimeline(markdown, snapshot);
            markdown.append("> No profiler timing samples collected. Runtime diagnostics are still current.\n");
            return markdown.toString();
        }

        List<SectionView> sectionsByTotal = snapshot.sections().stream()
                .sorted(Comparator.comparingLong(SectionView::totalNanos).reversed())
                .toList();
        List<SectionView> sectionsBySelf = snapshot.sections().stream()
                .sorted(Comparator.comparingLong(SectionView::selfNanos).reversed())
                .toList();
        List<SectionView> sectionsBySpike = snapshot.sections().stream()
                .sorted(Comparator.comparingLong(SectionView::maxNanos).reversed())
                .toList();
        SectionView topSection = sectionsByTotal.get(0);
        SectionView topSelfSection = sectionsBySelf.get(0);
        SectionView topSpike = sectionsBySpike.get(0);

        StringBuilder markdown = new StringBuilder(8192);
        appendReportHeader(markdown, snapshot);

        appendSessionContext(markdown, snapshot.context());
        appendRuntimeDiagnostics(markdown, snapshot.runtimeDiagnostics());
        appendStallWatchdog(markdown, snapshot.stalls());
        appendConnectionTimeline(markdown, snapshot);

        markdown.append("## Summary\n\n");
        markdown.append("- Hotspot by total time: <code>").append(escapeHtml(topSection.name())).append("</code> with <code>")
                .append(formatMillis(topSection.totalNanos())).append(" ms</code> total and <code>")
                .append(String.format(Locale.ROOT, "%.1f", topSection.sharePercent())).append("%</code> share.\n");
        markdown.append("- Hotspot by self time: <code>").append(escapeHtml(topSelfSection.name())).append("</code> with <code>")
                .append(formatMillis(topSelfSection.selfNanos())).append(" ms</code> self.\n");
        markdown.append("- Largest spike: <code>").append(escapeHtml(topSpike.name())).append("</code> with <code>")
                .append(formatMillis(topSpike.maxNanos())).append(" ms</code> max.\n");
        SectionView hudRender = findSection(snapshot, "hud/render");
        if (hudRender != null && hudRender.calls() > 0L) {
            markdown.append("- HUD render cost: <code>").append(formatMillis(hudRender.avgNanos()))
                    .append(" ms/frame</code> avg over <code>").append(hudRender.calls())
                    .append("</code> frames (peak <code>").append(formatMillis(hudRender.maxNanos())).append(" ms</code>).\n");
        }
        snapshot.stalls().stream()
                .max(Comparator.comparingLong(StallView::durationMs))
                .ifPresent(stall -> markdown.append("- Client watchdog: <code>")
                        .append(snapshot.stalls().size()).append("</code> stall(s), longest <code>")
                        .append(stall.durationMs()).append(" ms</code>.\n"));
        markdown.append("\n");

        appendHudFrameBreakdown(markdown, snapshot);
        appendSectionTable(markdown, "Top Sections By Total Time", sectionsByTotal.stream().limit(30).toList());
        appendSectionTable(markdown, "Top Sections By Self Time", sectionsBySelf.stream().limit(30).toList());
        appendSectionTable(markdown, "Largest Max Spikes", sectionsBySpike.stream().limit(20).toList());
        appendCallTree(markdown, "Call Tree By Total Time", snapshot.callTreeRoots());
        appendSampleTable(markdown, "Recent Samples", snapshot.samples().stream()
                .sorted(Comparator.comparingLong(ScopeSampleView::endedAtMs).reversed())
                .limit(SAMPLE_REPORT_LIMIT)
                .toList());
        appendSampleTable(markdown, "Worst Samples", snapshot.samples().stream()
                .sorted(Comparator.comparingLong(ScopeSampleView::totalNanos).reversed())
                .limit(SAMPLE_REPORT_LIMIT)
                .toList());

        List<CounterView> countersByTotal = snapshot.counters().stream()
                .sorted(Comparator.comparingLong(CounterView::total).reversed())
                .limit(25)
                .toList();
        appendCounterTable(markdown, "Top Counters", countersByTotal);

        markdown.append("<details>\n");
        markdown.append("<summary><strong>Legend</strong></summary>\n\n");
        markdown.append("- <code>total</code>: cumulative time spent in a section.\n");
        markdown.append("- <code>self</code>: time spent in a section excluding nested profiled children.\n");
        markdown.append("- <code>avg</code>: average time per call.\n");
        markdown.append("- <code>max</code>: worst single-call spike.\n");
        markdown.append("- <code>share</code>: section share of total measured profiler time.\n");
        markdown.append("- call tree: nested profiled scopes rendered as a text tree in Markdown.\n");
        markdown.append("- samples: bounded history of recent frame/tick-like profiled scopes.\n");
        markdown.append("- <code>events</code>: number of times a counter was incremented.\n");
        markdown.append("- <code>total</code> in counter tables: accumulated work units, not time.\n");
        markdown.append("</details>\n");
        return markdown.toString();
    }

    private void appendReportHeader(StringBuilder markdown, ReportSnapshot snapshot) {
        markdown.append("# FrogHelper Profiler Report\n\n");
        markdown.append("> Generated by `/fhprof dump` for local client-side diagnostics.\n\n");
        if (snapshot.focusPrefix() != null) {
            markdown.append("> Focused view for prefix: <code>").append(escapeHtml(snapshot.focusPrefix())).append("</code>\n\n");
        }
        markdown.append("<table>\n");
        markdown.append("  <tr><td><strong>Generated</strong></td><td><code>").append(REPORT_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(snapshot.generatedAtMs()))).append("</code></td></tr>\n");
        markdown.append("  <tr><td><strong>Enabled At Dump</strong></td><td><code>").append(snapshot.enabled()).append("</code></td></tr>\n");
        markdown.append("  <tr><td><strong>Session Duration</strong></td><td><code>").append(snapshot.sessionDurationMs()).append(" ms</code></td></tr>\n");
        markdown.append("  <tr><td><strong>Measured Time</strong></td><td><code>").append(formatMillis(snapshot.measuredNanos())).append(" ms</code></td></tr>\n");
        markdown.append("  <tr><td><strong>Section Count</strong></td><td><code>").append(snapshot.sections().size()).append("</code></td></tr>\n");
        markdown.append("  <tr><td><strong>Counter Count</strong></td><td><code>").append(snapshot.counters().size()).append("</code></td></tr>\n");
        markdown.append("</table>\n\n");
    }

    private static String formatRuntimeLine(RuntimeDiagnostics runtime) {
        return String.format(
                Locale.ROOT,
                "Runtime: heap=%s/%s, gc=%d collections (%dms), chat=%d/%d",
                formatMib(runtime.heapUsedBytes()),
                formatMib(runtime.heapMaxBytes()),
                runtime.gcCollections(),
                runtime.gcTimeMs(),
                runtime.chatMessages(),
                runtime.chatHistoryLimit()
        );
    }

    private static String formatWatchdogLine(List<StallView> stalls) {
        long longest = stalls.stream().mapToLong(StallView::durationMs).max().orElse(0L);
        return "Watchdog: stalls=" + stalls.size() + ", longest=" + longest + "ms";
    }

    /**
     * HUD-focused, per-frame view. FPS is about cost <em>per frame</em>, not session totals, so every
     * {@code hud/} / {@code widget/} / {@code ui/} section is normalised by the number of profiled HUD
     * frames ({@code hud/render} calls) into ms/frame and calls/frame — the numbers that actually map to
     * a frame budget (~16.7 ms at 60 fps).
     */
    private void appendHudFrameBreakdown(StringBuilder markdown, ReportSnapshot snapshot) {
        markdown.append("## HUD Render Breakdown (per frame)\n\n");

        SectionView hudRender = findSection(snapshot, "hud/render");
        long frames = hudRender != null ? hudRender.calls() : 0L;
        if (frames <= 0L) {
            markdown.append("> No <code>hud/render</code> frames recorded — profile while the in-game HUD is visible.\n\n");
            return;
        }

        List<SectionView> hudSections = snapshot.sections().stream()
                .filter(ModProfiler::isHudSection)
                .sorted(Comparator.comparingLong(SectionView::totalNanos).reversed())
                .limit(40)
                .toList();
        if (hudSections.isEmpty()) {
            markdown.append("> No HUD sections captured.\n\n");
            return;
        }

        markdown.append("Recorded HUD frames: <code>").append(frames)
                .append("</code> · avg <code>hud/render</code>: <code>").append(formatMillis(hudRender.avgNanos()))
                .append(" ms/frame</code> · worst frame: <code>").append(formatMillis(hudRender.maxNanos()))
                .append(" ms</code> · frame budget @60fps: <code>16.667 ms</code>.\n\n");

        markdown.append("| Section | Calls/frame | ms/frame | Avg ms | Max ms | Total ms | Share |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (SectionView section : hudSections) {
            double msPerFrame = nanosToMillis(section.totalNanos()) / frames;
            double callsPerFrame = section.calls() / (double) frames;
            markdown.append("| <code>").append(escapePipe(section.name())).append("</code> | ")
                    .append(String.format(Locale.ROOT, "%.1f", callsPerFrame)).append(" | ")
                    .append(String.format(Locale.ROOT, "%.3f", msPerFrame)).append(" | ")
                    .append(formatMillis(section.avgNanos())).append(" | ")
                    .append(formatMillis(section.maxNanos())).append(" | ")
                    .append(formatMillis(section.totalNanos())).append(" | ")
                    .append(String.format(Locale.ROOT, "%.1f%%", section.sharePercent())).append(" |\n");
        }
        markdown.append("\n");
    }

    private static boolean isHudSection(SectionView section) {
        String name = section.name();
        return name.startsWith("hud/") || name.startsWith("widget/") || name.startsWith("ui/");
    }

    private SectionView findSection(ReportSnapshot snapshot, String name) {
        return snapshot.sections().stream()
                .filter(section -> section.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private void appendSectionTable(StringBuilder markdown, String title, List<SectionView> sections) {
        markdown.append("## ").append(title).append("\n\n");
        if (sections.isEmpty()) {
            markdown.append("> No matching sections.\n\n");
            return;
        }

        markdown.append("| Section | Calls | Total ms | Self ms | Avg ms | Avg Self ms | Max ms | Share |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (SectionView section : sections) {
            markdown.append("| <code>").append(escapePipe(section.name())).append("</code> | ")
                    .append(section.calls()).append(" | ")
                    .append(formatMillis(section.totalNanos())).append(" | ")
                    .append(formatMillis(section.selfNanos())).append(" | ")
                    .append(formatMillis(section.avgNanos())).append(" | ")
                    .append(formatMillis(section.avgSelfNanos())).append(" | ")
                    .append(formatMillis(section.maxNanos())).append(" | ")
                    .append(String.format(Locale.ROOT, "%.1f%%", section.sharePercent())).append(" |\n");
        }
        markdown.append("\n");
    }

    private void appendCallTree(StringBuilder markdown, String title, List<CallTreeNodeView> roots) {
        markdown.append("## ").append(title).append("\n\n");
        if (roots.isEmpty()) {
            markdown.append("> No profiled call tree captured.\n\n");
            return;
        }

        markdown.append("```text\n");
        List<CallTreeNodeView> sortedRoots = roots.stream()
                .sorted(Comparator.comparingLong(CallTreeNodeView::totalNanos).reversed())
                .limit(CALL_TREE_CHILD_LIMIT)
                .toList();
        for (int i = 0; i < sortedRoots.size(); i++) {
            CallTreeNodeView root = sortedRoots.get(i);
            appendCallTreeLine(markdown, root, "", i == sortedRoots.size() - 1, 0, Math.max(1L, root.totalNanos()));
        }
        markdown.append("```\n\n");
    }

    private void appendCallTreeLine(StringBuilder markdown, CallTreeNodeView node, String prefix, boolean lastChild, int depth, long rootTotalNanos) {
        double shareOfRoot = rootTotalNanos <= 0L ? 0.0 : node.totalNanos() * 100.0 / rootTotalNanos;
        markdown.append(prefix);
        if (depth > 0) {
            markdown.append(lastChild ? "\\- " : "|- ");
        }
        markdown.append(node.name())
                .append(" [total=").append(formatMillis(node.totalNanos())).append(" ms")
                .append(", share=").append(String.format(Locale.ROOT, "%.1f%%", shareOfRoot))
                .append(", self=").append(formatMillis(node.selfNanos())).append(" ms")
                .append(", calls=").append(node.calls())
                .append(", max=").append(formatMillis(node.maxNanos())).append(" ms")
                .append("]\n");

        String childPrefix = prefix + (lastChild ? "   " : "|  ");
        if (depth >= CALL_TREE_DEPTH_LIMIT) {
            if (!node.children().isEmpty()) {
                markdown.append(childPrefix).append("`- ...\n");
            }
            return;
        }

        List<CallTreeNodeView> children = node.children().stream()
                .sorted(Comparator.comparingLong(CallTreeNodeView::totalNanos).reversed())
                .limit(CALL_TREE_CHILD_LIMIT)
                .toList();
        for (int i = 0; i < children.size(); i++) {
            appendCallTreeLine(markdown, children.get(i), childPrefix, i == children.size() - 1, depth + 1, rootTotalNanos);
        }
        if (node.children().size() > children.size()) {
            markdown.append(childPrefix).append("`- ...\n");
        }
    }

    private void appendCounterTable(StringBuilder markdown, String title, List<CounterView> counters) {
        markdown.append("## ").append(title).append("\n\n");
        if (counters.isEmpty()) {
            markdown.append("> No matching counters.\n\n");
            return;
        }

        markdown.append("| Counter | Events | Total | Avg | Max Delta |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: |\n");
        for (CounterView counter : counters) {
            markdown.append("| <code>").append(escapePipe(counter.name())).append("</code> | ")
                    .append(counter.events()).append(" | ")
                    .append(counter.total()).append(" | ")
                    .append(String.format(Locale.ROOT, "%.2f", counter.avg())).append(" | ")
                    .append(counter.maxDelta()).append(" |\n");
        }
        markdown.append("\n");
    }

    private void appendSampleTable(StringBuilder markdown, String title, List<ScopeSampleView> samples) {
        markdown.append("## ").append(title).append("\n\n");
        if (samples.isEmpty()) {
            markdown.append("> No matching samples.\n\n");
            return;
        }

        markdown.append("| Section | Ended | Total ms | Self ms | Thread |\n");
        markdown.append("| --- | --- | ---: | ---: | --- |\n");
        for (ScopeSampleView sample : samples) {
            markdown.append("| <code>").append(escapePipe(sample.section())).append("</code> | <code>")
                    .append(REPORT_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(sample.endedAtMs()))).append("</code> | ")
                    .append(formatMillis(sample.totalNanos())).append(" | ")
                    .append(formatMillis(sample.selfNanos())).append(" | <code>")
                    .append(escapePipe(sample.threadName())).append("</code> |\n");
        }
        markdown.append("\n");
    }

    private void appendSessionContext(StringBuilder markdown, SessionContext context) {
        markdown.append("## Session Context\n\n");
        markdown.append("| Field | Value |\n");
        markdown.append("| --- | --- |\n");
        appendContextRow(markdown, "FrogHelper", context.modVersion());
        appendContextRow(markdown, "Minecraft", context.minecraftVersion());
        appendContextRow(markdown, "Fabric Loader", context.fabricLoaderVersion());
        appendContextRow(markdown, "Environment", context.environment());
        appendContextRow(markdown, "Server", context.serverName());
        appendContextRow(markdown, "Screen", context.screenName());
        appendContextRow(markdown, "Screen Title", context.screenTitle());
        appendContextRow(markdown, "Dimension", context.dimension());
        appendContextRow(markdown, "Player", context.playerName());
        appendContextRow(markdown, "FPS", context.fps());
        appendContextRow(markdown, "Window", context.windowSize());
        markdown.append("\n");
    }

    private void appendRuntimeDiagnostics(StringBuilder markdown, RuntimeDiagnostics runtime) {
        markdown.append("## Runtime Diagnostics\n\n");
        markdown.append("| Metric | Value |\n");
        markdown.append("| --- | ---: |\n");
        appendContextRow(markdown, "JVM uptime", runtime.jvmUptimeMs() + " ms");
        appendContextRow(markdown, "Heap used", formatMib(runtime.heapUsedBytes()));
        appendContextRow(markdown, "Heap committed", formatMib(runtime.heapCommittedBytes()));
        appendContextRow(markdown, "Heap max", formatMib(runtime.heapMaxBytes()));
        appendContextRow(markdown, "GC collections", Long.toString(runtime.gcCollections()));
        appendContextRow(markdown, "GC time", runtime.gcTimeMs() + " ms");
        appendContextRow(markdown, "Chat messages retained", Integer.toString(runtime.chatMessages()));
        appendContextRow(markdown, "Chat tab references", Integer.toString(runtime.chatTabReferences()));
        appendContextRow(markdown, "Chat history limit", Integer.toString(runtime.chatHistoryLimit()));
        appendContextRow(markdown, "Chat reconnect limit", Integer.toString(runtime.chatReconnectLimit()));
        markdown.append("\n");
    }

    private void appendStallWatchdog(StringBuilder markdown, List<StallView> stalls) {
        markdown.append("## Client Stall Watchdog\n\n");
        if (stalls.isEmpty()) {
            markdown.append("> No client tick stalls of 750 ms or longer were captured.\n\n");
            return;
        }

        markdown.append("| Started | Duration | State | Captured Threads |\n");
        markdown.append("| --- | ---: | --- | ---: |\n");
        for (StallView stall : stalls) {
            markdown.append("| <code>").append(REPORT_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(stall.startedAtMs())))
                    .append("</code> | <code>").append(stall.durationMs()).append(" ms</code> | ")
                    .append(stall.recovered() ? "recovered" : "active at dump")
                    .append(" | <code>").append(stall.threadStacks().size()).append("</code> |\n");
        }
        markdown.append("\n");

        for (int stallIndex = 0; stallIndex < stalls.size(); stallIndex++) {
            StallView stall = stalls.get(stallIndex);
            markdown.append("<details>\n");
            markdown.append("<summary><strong>Stall ").append(stallIndex + 1)
                    .append("</strong>: ").append(stall.durationMs()).append(" ms</summary>\n\n");
            if (stall.threadStacks().isEmpty()) {
                markdown.append("> Thread stacks were unavailable.\n\n");
            }
            for (ThreadStackView thread : stall.threadStacks()) {
                markdown.append("### <code>").append(escapeHtml(thread.threadName())).append("</code> (")
                        .append(escapeHtml(thread.state())).append(")\n\n");
                markdown.append("```text\n");
                if (thread.frames().isEmpty()) {
                    markdown.append("<empty stack>\n");
                } else {
                    for (String frame : thread.frames()) {
                        markdown.append("at ").append(frame).append("\n");
                    }
                }
                markdown.append("```\n\n");
            }
            markdown.append("</details>\n\n");
        }
    }

    private void appendConnectionTimeline(StringBuilder markdown, ReportSnapshot snapshot) {
        markdown.append("## Connection Timeline\n\n");
        if (snapshot.timeline().isEmpty()) {
            markdown.append("> No connection lifecycle events were captured.\n\n");
            return;
        }

        markdown.append("| Time | Session +ms | Event | Detail |\n");
        markdown.append("| --- | ---: | --- | --- |\n");
        for (TimelineEventView event : snapshot.timeline()) {
            long sessionOffset = snapshot.sessionStartedAtMs() <= 0L
                    ? 0L
                    : Math.max(0L, event.timestampMs() - snapshot.sessionStartedAtMs());
            markdown.append("| <code>").append(REPORT_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(event.timestampMs())))
                    .append("</code> | <code>").append(sessionOffset)
                    .append("</code> | <code>").append(escapePipe(event.event()))
                    .append("</code> | <code>").append(escapePipe(event.detail()))
                    .append("</code> |\n");
        }
        markdown.append("\n");
    }

    private void appendContextRow(StringBuilder markdown, String name, String value) {
        markdown.append("| ").append(name).append(" | <code>").append(escapePipe(value)).append("</code> |\n");
    }

    private String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanosToMillis(nanos));
    }

    private static String formatMib(long bytes) {
        if (bytes < 0L) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.1f MiB", bytes / 1_048_576.0);
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private String escapePipe(String value) {
        return escapeHtml(value).replace("|", "\\|");
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private ReportSnapshot filterSnapshot(ReportSnapshot snapshot, String prefixFilter) {
        String normalizedPrefix = normalizePrefix(prefixFilter);
        if (normalizedPrefix == null) {
            return snapshot;
        }

        List<SectionView> sections = snapshot.sections().stream()
                .filter(section -> section.name().startsWith(normalizedPrefix))
                .toList();
        List<CounterView> counters = snapshot.counters().stream()
                .filter(counter -> counter.name().startsWith(normalizedPrefix))
                .toList();
        List<CallTreeNodeView> callTreeRoots = filterCallTree(snapshot.callTreeRoots(), normalizedPrefix);
        List<ScopeSampleView> samples = snapshot.samples().stream()
                .filter(sample -> sample.section().startsWith(normalizedPrefix))
                .toList();
        long measuredNanos = sections.stream().mapToLong(SectionView::totalNanos).sum();
        return new ReportSnapshot(
                snapshot.enabled(),
                snapshot.sessionStartedAtMs(),
                snapshot.sessionStoppedAtMs(),
                snapshot.sessionDurationMs(),
                snapshot.generatedAtMs(),
                measuredNanos,
                sections,
                counters,
                callTreeRoots,
                samples,
                snapshot.context(),
                snapshot.runtimeDiagnostics(),
                snapshot.stalls(),
                snapshot.timeline(),
                normalizedPrefix
        );
    }

    private List<CallTreeNodeView> filterCallTree(List<CallTreeNodeView> nodes, String prefix) {
        List<CallTreeNodeView> filtered = new ArrayList<>();
        for (CallTreeNodeView node : nodes) {
            CallTreeNodeView filteredNode = filterCallTreeNode(node, prefix);
            if (filteredNode != null) {
                filtered.add(filteredNode);
            }
        }
        return filtered;
    }

    private CallTreeNodeView filterCallTreeNode(CallTreeNodeView node, String prefix) {
        if (node.name().startsWith(prefix)) {
            return node;
        }

        List<CallTreeNodeView> filteredChildren = filterCallTree(node.children(), prefix);
        if (filteredChildren.isEmpty()) {
            return null;
        }

        return new CallTreeNodeView(node.name(), node.calls(), node.totalNanos(), node.selfNanos(), node.maxNanos(), filteredChildren);
    }

    private String normalizePrefix(String prefixFilter) {
        if (prefixFilter == null) {
            return null;
        }
        String normalized = prefixFilter.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String sanitizeFileComponent(String value) {
        StringBuilder sanitized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_') {
                sanitized.append(ch);
            } else {
                sanitized.append('-');
            }
        }
        return sanitized.toString().replaceAll("-{2,}", "-");
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "n/a" : value;
    }

    private boolean shouldCaptureSample(String section, ActiveScope parentScope) {
        if (section == null || section.isBlank()) {
            return false;
        }
        if (section.endsWith("/frame")) {
            return true;
        }
        if (section.startsWith("tick/") || section.startsWith("world/")) {
            return true;
        }
        if (section.startsWith("ui/") && section.endsWith("/render")) {
            return true;
        }
        if (parentScope == null) {
            return section.startsWith("render/")
                    || section.startsWith("hud/")
                    || section.startsWith("ui/")
                    || section.startsWith("protocol/");
        }
        return false;
    }

    private long nanosToMillisRounded(long nanos) {
        return Math.round(nanos / 1_000_000.0);
    }

    private String resolveModVersion(String modId) {
        Optional<String> version = FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString());
        return version.orElse("unknown");
    }

    private CallTreeNodeView toCallTreeView(String name, CallTreeNodeStats stats) {
        List<CallTreeNodeView> children = new ArrayList<>(stats.children.size());
        for (Map.Entry<String, CallTreeNodeStats> entry : stats.children.entrySet()) {
            children.add(toCallTreeView(entry.getKey(), entry.getValue()));
        }
        return new CallTreeNodeView(name, stats.calls, stats.totalNanos, stats.selfNanos, stats.maxNanos, children);
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    public record SectionView(String name, long calls, long totalNanos, long selfNanos, long avgNanos, long avgSelfNanos, long maxNanos, double sharePercent) {
    }

    public record CounterView(String name, long events, long total, long maxDelta, double avg) {
    }

    public record CallTreeNodeView(String name, long calls, long totalNanos, long selfNanos, long maxNanos, List<CallTreeNodeView> children) {
    }

    public record ScopeSampleView(
            String section,
            long startedAtMs,
            long endedAtMs,
            long totalNanos,
            long selfNanos,
            String threadName
    ) {
    }

    public record ThreadStackView(
            String threadName,
            String state,
            List<String> frames
    ) {
    }

    public record StallView(
            long startedAtMs,
            long detectedAtMs,
            long recoveredAtMs,
            long durationMs,
            boolean recovered,
            List<ThreadStackView> threadStacks
    ) {
    }

    public record TimelineEventView(
            long timestampMs,
            String event,
            String detail
    ) {
    }

    public record SessionContext(
            String modVersion,
            String minecraftVersion,
            String fabricLoaderVersion,
            String environment,
            String serverName,
            String screenName,
            String screenTitle,
            String dimension,
            String playerName,
            String fps,
            String windowSize
    ) {
        private static SessionContext capture() {
            Minecraft minecraft = Minecraft.getInstance();
            Screen screen = minecraft.screen;
            ServerData server = minecraft.getCurrentServer();
            String serverName = server != null ? server.name : (minecraft.hasSingleplayerServer() ? "singleplayer" : "menu");
            String screenName = screen != null ? screen.getClass().getSimpleName() : "none";
            String screenTitle = screen != null ? screen.getTitle().getString() : "n/a";
            String dimension = minecraft.level != null ? minecraft.level.dimension().location().toString() : "n/a";
            String playerName = minecraft.player != null ? minecraft.player.getGameProfile().getName() : "n/a";
            String fps = safeStaticInt(minecraft.getFps());
            String windowSize = minecraft.getWindow().getGuiScaledWidth() + "x" + minecraft.getWindow().getGuiScaledHeight();
            return new SessionContext(
                    INSTANCE.resolveModVersion(MOD_ID),
                    SharedConstants.getCurrentVersion().getName(),
                    INSTANCE.resolveModVersion("fabricloader"),
                    FabricLoader.getInstance().getEnvironmentType().name().toLowerCase(Locale.ROOT),
                    INSTANCE.safeValue(serverName),
                    INSTANCE.safeValue(screenName),
                    INSTANCE.safeValue(screenTitle),
                    INSTANCE.safeValue(dimension),
                    INSTANCE.safeValue(playerName),
                    INSTANCE.safeValue(fps),
                    INSTANCE.safeValue(windowSize)
            );
        }

        private static String safeStaticInt(int value) {
            return value > 0 ? Integer.toString(value) : "n/a";
        }
    }

    public record RuntimeDiagnostics(
            long jvmUptimeMs,
            long heapUsedBytes,
            long heapCommittedBytes,
            long heapMaxBytes,
            long gcCollections,
            long gcTimeMs,
            int chatMessages,
            int chatTabReferences,
            int chatHistoryLimit,
            int chatReconnectLimit
    ) {
        private static RuntimeDiagnostics capture() {
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            long gcCollections = 0L;
            long gcTimeMs = 0L;
            for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (collector.getCollectionCount() >= 0L) {
                    gcCollections += collector.getCollectionCount();
                }
                if (collector.getCollectionTime() >= 0L) {
                    gcTimeMs += collector.getCollectionTime();
                }
            }

            ChatTabManager.ArchiveSnapshot chat = ChatTabManager.getInstance().archiveSnapshot();
            return new RuntimeDiagnostics(
                    ManagementFactory.getRuntimeMXBean().getUptime(),
                    heap.getUsed(),
                    heap.getCommitted(),
                    heap.getMax(),
                    gcCollections,
                    gcTimeMs,
                    chat.uniqueMessages(),
                    chat.tabReferences(),
                    chat.historyLimit(),
                    chat.reconnectLimit()
            );
        }
    }

    public record ReportSnapshot(
            boolean enabled,
            long sessionStartedAtMs,
            long sessionStoppedAtMs,
            long sessionDurationMs,
            long generatedAtMs,
            long measuredNanos,
            List<SectionView> sections,
            List<CounterView> counters,
            List<CallTreeNodeView> callTreeRoots,
            List<ScopeSampleView> samples,
            SessionContext context,
            RuntimeDiagnostics runtimeDiagnostics,
            List<StallView> stalls,
            List<TimelineEventView> timeline,
            String focusPrefix
    ) {
    }

    private static final class SectionStats {
        private long calls;
        private long totalNanos;
        private long selfNanos;
        private long maxNanos;
    }

    private static final class CounterStats {
        private long events;
        private long total;
        private long maxDelta;
    }

    private static final class CallTreeNodeStats {
        private final Map<String, CallTreeNodeStats> children = new LinkedHashMap<>();
        private long calls;
        private long totalNanos;
        private long selfNanos;
        private long maxNanos;
    }

    private static final class ActiveScope {
        private final String section;
        private final long startedAtNanos;
        private final ActiveScope parent;
        private long childNanos;

        private ActiveScope(String section, long startedAtNanos, ActiveScope parent) {
            this.section = section;
            this.startedAtNanos = startedAtNanos;
            this.parent = parent;
        }
    }

    private static final class ScopeSample {
        private final String section;
        private final long totalNanos;
        private final long selfNanos;
        private final long startedAtMs;
        private final long endedAtMs;
        private final String threadName;

        private ScopeSample(String section, long totalNanos, long selfNanos, long startedAtMs, long endedAtMs, String threadName) {
            this.section = section;
            this.totalNanos = totalNanos;
            this.selfNanos = selfNanos;
            this.startedAtMs = startedAtMs;
            this.endedAtMs = endedAtMs;
            this.threadName = threadName;
        }
    }

    private static final class StallCapture {
        private final long startedAtMs;
        private final long detectedAtMs;
        private long recoveredAtMs;
        private List<ThreadStackView> threadStacks = List.of();

        private StallCapture(long startedAtMs, long detectedAtMs) {
            this.startedAtMs = startedAtMs;
            this.detectedAtMs = detectedAtMs;
        }

        private StallView toView(long snapshotAtMs) {
            boolean recovered = recoveredAtMs > 0L;
            long end = recovered ? recoveredAtMs : snapshotAtMs;
            return new StallView(
                    startedAtMs,
                    detectedAtMs,
                    recoveredAtMs,
                    Math.max(0L, end - startedAtMs),
                    recovered,
                    threadStacks
            );
        }
    }

    private record TimelineEvent(long timestampMs, String event, String detail) {
    }
}
