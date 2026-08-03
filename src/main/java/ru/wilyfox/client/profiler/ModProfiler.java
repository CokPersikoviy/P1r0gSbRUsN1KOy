package ru.wilyfox.client.profiler;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.Packet;
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
import java.util.function.ToLongFunction;

import static ru.wilyfox.FrogHelper.MOD_ID;

public final class ModProfiler {
    private static final ModProfiler INSTANCE = new ModProfiler();
    private static final Scope NOOP_SCOPE = () -> { };
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());
    private static final int CALL_TREE_CHILD_LIMIT = 12;
    private static final int CALL_TREE_DEPTH_LIMIT = 6;
    private static final int SAMPLE_HISTORY_LIMIT = 256;
    private static final long WATCHDOG_POLL_MILLIS = 250L;
    private static final long STALL_THRESHOLD_NANOS = 750_000_000L;
    private static final int STALL_HISTORY_LIMIT = 12;
    private static final int TIMELINE_HISTORY_LIMIT = 128;
    private static final int STACK_TRACE_LIMIT = 48;
    private static final int DIAGNOSTIC_THREAD_LIMIT = 64;
    private static final long PERSISTENT_SAMPLE_INTERVAL_MS = 10_000L;
    private static final int PERSISTENT_SAMPLE_HISTORY_LIMIT = 8_640;
    private static final int LIFETIME_TIMELINE_HISTORY_LIMIT = 2_048;

    private final Map<String, SectionStats> statsBySection = new LinkedHashMap<>();
    private final Map<String, CounterStats> countersByName = new LinkedHashMap<>();
    private final Map<String, CallTreeNodeStats> rootNodes = new LinkedHashMap<>();
    private final Deque<ScopeSample> recentSamples = new ArrayDeque<>();
    private final Deque<StallCapture> stallCaptures = new ArrayDeque<>();
    private final Deque<TimelineEvent> connectionTimeline = new ArrayDeque<>();
    private final Deque<TimelineEvent> lifetimeTimeline = new ArrayDeque<>();
    private final Deque<ProfilerDiagnostics.DiagnosticSample> persistentSamples = new ArrayDeque<>();
    private final ThreadLocal<Deque<ActiveScope>> activeScopes = ThreadLocal.withInitial(ArrayDeque::new);
    private final AtomicBoolean diagnosticsRegistered = new AtomicBoolean();
    private volatile boolean enabled;
    private long sessionStartedAt;
    private long sessionStoppedAt;
    private long lastClientHeartbeatNanos;
    private long lastClientHeartbeatAtMs;
    private long lastProtocolPayloadAtMs;
    private long lastPersistentSampleAtMs;
    private long lifetimeProtocolPayloadCount;
    private long lifetimeProtocolPayloadBytes;
    private long lifetimeJoinCount;
    private long lifetimeDisconnectCount;
    private long lifetimeDimensionChangeCount;
    private Thread clientThread;
    private StallCapture activeStall;
    private String observedDimension = "";
    private volatile boolean diagnosticCaptureInProgress;

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
            synchronized (this) {
                lifetimeJoinCount++;
            }
            recordTimelineEvent("connection/join", handler.getLocalGameProfile().getName());
            observeDimension(client, true);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            synchronized (this) {
                lifetimeDisconnectCount++;
            }
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
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void recordProtocolHandshake(String phase) {
        recordTimelineEvent("protocol/handshake/" + safeTimelineValue(phase), currentDimension(Minecraft.getInstance()));
    }

    public void recordClientEvent(String event, String detail) {
        recordTimelineEvent("client/" + safeTimelineValue(event), detail);
    }

    public void recordNetworkPacket(String direction, Packet<?> packet) {
        if (!isEnabled() || packet == null) {
            return;
        }
        String type = packet.getClass().getSimpleName();
        incrementCounter("network/" + safeSectionComponent(direction) + "/packets");
        incrementCounter("network/" + safeSectionComponent(direction) + "/type/" + safeSectionComponent(type));
    }

    public Scope typedScope(String prefix, Object typeKey) {
        if (!isEnabled()) {
            return NOOP_SCOPE;
        }
        return scope(typedSection(prefix, typeKey));
    }

    public String typedSection(String prefix, Object typeKey) {
        String type = typeKey == null ? "unknown" : typeKey.toString();
        return prefix + "/" + safeSectionComponent(type);
    }

    public synchronized void recordProtocolPayloadReceived(int payloadBytes) {
        lifetimeProtocolPayloadCount++;
        lifetimeProtocolPayloadBytes += Math.max(0, payloadBytes);
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
        boolean capturePersistentSample;
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
            capturePersistentSample = lastPersistentSampleAtMs <= 0L
                    || nowMs - lastPersistentSampleAtMs >= PERSISTENT_SAMPLE_INTERVAL_MS;
            if (capturePersistentSample) {
                lastPersistentSampleAtMs = nowMs;
            }
        }
        observeDimension(minecraft, false);
        if (capturePersistentSample) {
            capturePersistentSample(minecraft);
        }
    }

    private void capturePersistentSample(Minecraft minecraft) {
        ProfilerDiagnostics.DiagnosticSample sample;
        try {
            sample = ProfilerDiagnostics.captureSample(minecraft);
        } catch (Throwable ignored) {
            return;
        }
        synchronized (this) {
            persistentSamples.addLast(sample);
            while (persistentSamples.size() > PERSISTENT_SAMPLE_HISTORY_LIMIT) {
                persistentSamples.removeFirst();
            }
        }
    }

    private void observeDimension(Minecraft minecraft, boolean force) {
        String dimension = currentDimension(minecraft);
        synchronized (this) {
            if (!force && dimension.equals(observedDimension)) {
                return;
            }

            String previous = observedDimension;
            observedDimension = dimension;
            lastProtocolPayloadAtMs = 0L;
            if (!previous.isBlank() && !previous.equals(dimension)) {
                lifetimeDimensionChangeCount++;
            }
            String event = previous.isBlank() ? "dimension/observed" : "dimension/change";
            String detail = previous.isBlank() || previous.equals("n/a")
                    ? dimension
                    : previous + " -> " + dimension;
            recordLifetimeTimelineEventLocked(event, detail);
            if (enabled) {
                recordSessionTimelineEventLocked(event, detail);
            }
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
                    || diagnosticCaptureInProgress
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
        ProfilerDiagnostics.DiagnosticSample diagnostics;
        try {
            diagnostics = ProfilerDiagnostics.captureSample(null);
        } catch (Throwable ignored) {
            diagnostics = null;
        }
        synchronized (this) {
            capture.threadStacks = stacks;
            capture.diagnostics = diagnostics;
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
        return !normalized.equals("froghelper-profiler-watchdog");
    }

    private int diagnosticThreadPriority(Thread thread) {
        String normalized = thread.getName().toLowerCase(Locale.ROOT);
        return normalized.contains("netty")
                || normalized.contains("client io")
                || normalized.contains("server connector")
                || normalized.contains("network")
                ? 0
                : normalized.contains("worker")
                || normalized.contains("forkjoin")
                || normalized.contains("sound")
                || normalized.contains("render")
                ? 1
                : 2;
    }

    private void recordTimelineEvent(String event, String detail) {
        synchronized (this) {
            recordLifetimeTimelineEventLocked(event, detail);
            if (enabled) {
                recordSessionTimelineEventLocked(event, detail);
            }
        }
    }

    private void recordTimelineEventLocked(String event, String detail) {
        recordLifetimeTimelineEventLocked(event, detail);
        recordSessionTimelineEventLocked(event, detail);
    }

    private void recordSessionTimelineEventLocked(String event, String detail) {
        connectionTimeline.addLast(new TimelineEvent(
                System.currentTimeMillis(),
                safeTimelineValue(event),
                safeTimelineValue(detail)
        ));
        while (connectionTimeline.size() > TIMELINE_HISTORY_LIMIT) {
            connectionTimeline.removeFirst();
        }
    }

    private void recordLifetimeTimelineEventLocked(String event, String detail) {
        lifetimeTimeline.addLast(new TimelineEvent(
                System.currentTimeMillis(),
                safeTimelineValue(event),
                safeTimelineValue(detail)
        ));
        while (lifetimeTimeline.size() > LIFETIME_TIMELINE_HISTORY_LIMIT) {
            lifetimeTimeline.removeFirst();
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

    private static String safeSectionComponent(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        StringBuilder sanitized = new StringBuilder(Math.min(96, value.length()));
        for (int index = 0; index < value.length() && sanitized.length() < 96; index++) {
            char ch = value.charAt(index);
            sanitized.append(Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.' ? ch : '_');
        }
        return sanitized.toString();
    }

    public Scope scope(String section) {
        if (!isEnabled() || section == null || section.isBlank()) {
            return NOOP_SCOPE;
        }

        Deque<ActiveScope> stack = activeScopes.get();
        ActiveScope scope = new ActiveScope(this, section, System.nanoTime(), stack.peekLast(), stack);
        stack.addLast(scope);
        return scope;
    }

    static boolean isNoopScope(Scope scope) {
        return scope == null || scope == NOOP_SCOPE;
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

    public List<String> buildReportLines() {
        return buildReportLines(null);
    }

    public List<String> buildReportLines(String prefixFilter) {
        ReportSnapshot snapshot = filterSnapshot(snapshot(false), prefixFilter);
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
                + ", persistentSamples=" + persistentSamples.size()
                + ", lifetimeEvents=" + lifetimeTimeline.size()
                + ", sessionMs=" + sessionDurationMs();
    }

    public Path writeMarkdownReport(Path directory) throws IOException {
        return writeMarkdownReport(directory, null);
    }

    public Path writeMarkdownReport(Path directory, String prefixFilter) throws IOException {
        recordTimelineEvent("profiler/dump", normalizePrefix(prefixFilter) == null ? "full" : prefixFilter);
        ReportSnapshot snapshot = filterSnapshot(snapshot(true), prefixFilter);
        String timestamp = FILE_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(snapshot.generatedAtMs()));
        String baseName = snapshot.focusPrefix() == null
                ? "fhprof-" + timestamp
                : "fhprof-" + sanitizeFileComponent(snapshot.focusPrefix()) + "-" + timestamp;
        Path output = directory.resolve(baseName + ".md");
        Files.createDirectories(directory);
        Files.writeString(output, buildMarkdownReport(snapshot), StandardCharsets.UTF_8);
        return output.toAbsolutePath().normalize();
    }

    private ReportSnapshot snapshot(boolean includeHistogram) {
        diagnosticCaptureInProgress = true;
        try {
            ProfilerDiagnostics.FullDiagnostics fullDiagnostics =
                    ProfilerDiagnostics.captureFull(Minecraft.getInstance(), includeHistogram);
            synchronized (this) {
                lastClientHeartbeatNanos = System.nanoTime();
                lastClientHeartbeatAtMs = System.currentTimeMillis();
                return snapshotLocked(fullDiagnostics);
            }
        } finally {
            diagnosticCaptureInProgress = false;
        }
    }

    private ReportSnapshot snapshotLocked(ProfilerDiagnostics.FullDiagnostics fullDiagnostics) {
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
        List<TimelineEventView> lifetimeTimelineView = lifetimeTimeline.stream()
                .map(event -> new TimelineEventView(event.timestampMs, event.event, event.detail))
                .toList();
        List<ProfilerDiagnostics.DiagnosticSample> persistentSampleViews = List.copyOf(persistentSamples);
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
                persistentSampleViews,
                fullDiagnostics,
                lifetimeTimelineView,
                lifetimeSnapshot(),
                null
        );
    }

    synchronized LifetimeView lifetimeSnapshot() {
        return new LifetimeView(
                lifetimeProtocolPayloadCount,
                lifetimeProtocolPayloadBytes,
                lifetimeJoinCount,
                lifetimeDisconnectCount,
                lifetimeDimensionChangeCount
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

        Map<String, CallTreeNodeStats> targetMap = callTreeChildren(parentScope);
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

    private Map<String, CallTreeNodeStats> callTreeChildren(ActiveScope scope) {
        if (scope == null) {
            return rootNodes;
        }
        Map<String, CallTreeNodeStats> parentChildren = callTreeChildren(scope.parent);
        CallTreeNodeStats node = parentChildren.computeIfAbsent(scope.section, ignored -> new CallTreeNodeStats());
        return node.children;
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
            appendLongRunningDiagnostics(markdown, snapshot);
            appendFullDiagnostics(markdown, snapshot.fullDiagnostics());
            appendStallWatchdog(markdown, snapshot.stalls());
            appendConnectionTimeline(markdown, snapshot);
            appendLifetimeTimeline(markdown, snapshot);
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
        appendLongRunningDiagnostics(markdown, snapshot);
        appendFullDiagnostics(markdown, snapshot.fullDiagnostics());
        appendStallWatchdog(markdown, snapshot.stalls());
        appendConnectionTimeline(markdown, snapshot);
        appendLifetimeTimeline(markdown, snapshot);

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
        appendSectionTable(markdown, "All Sections By Total Time", sectionsByTotal);
        appendSectionTable(markdown, "All Sections By Self Time", sectionsBySelf);
        appendSectionTable(markdown, "All Sections By Max Spike", sectionsBySpike);
        appendCallTree(markdown, "Call Tree By Total Time", snapshot.callTreeRoots());
        appendSampleTable(markdown, "Recent Samples", snapshot.samples().stream()
                .sorted(Comparator.comparingLong(ScopeSampleView::endedAtMs).reversed())
                .toList());
        appendSampleTable(markdown, "Worst Samples", snapshot.samples().stream()
                .sorted(Comparator.comparingLong(ScopeSampleView::totalNanos).reversed())
                .toList());

        List<CounterView> countersByTotal = snapshot.counters().stream()
                .sorted(Comparator.comparingLong(CounterView::total).reversed())
                .toList();
        appendCounterTable(markdown, "All Counters", countersByTotal);

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

    private void appendLongRunningDiagnostics(StringBuilder markdown, ReportSnapshot snapshot) {
        markdown.append("## Long-Running Diagnostics\n\n");
        markdown.append("> Recorded continuously from client startup. `/fhprof reset` and `/fhprof start` do not clear this data.\n\n");

        LifetimeView lifetime = snapshot.lifetime();
        markdown.append("| Lifetime Event | Value |\n");
        markdown.append("| --- | ---: |\n");
        appendContextRow(markdown, "Protocol payloads", Long.toString(lifetime.protocolPayloadCount()));
        appendContextRow(markdown, "Protocol payload bytes", Long.toString(lifetime.protocolPayloadBytes()));
        appendContextRow(markdown, "Server joins", Long.toString(lifetime.joins()));
        appendContextRow(markdown, "Disconnects", Long.toString(lifetime.disconnects()));
        appendContextRow(markdown, "Dimension changes", Long.toString(lifetime.dimensionChanges()));
        markdown.append("\n");

        List<ProfilerDiagnostics.DiagnosticSample> samples = new ArrayList<>(snapshot.persistentSamples());
        ProfilerDiagnostics.DiagnosticSample current = snapshot.fullDiagnostics() != null
                ? snapshot.fullDiagnostics().sample()
                : null;
        if (current != null && (samples.isEmpty() || samples.get(samples.size() - 1).capturedAtMs() != current.capturedAtMs())) {
            samples.add(current);
        }
        if (samples.isEmpty()) {
            markdown.append("> No persistent diagnostic samples have been captured yet.\n\n");
            return;
        }

        markdown.append("Samples: <code>").append(samples.size()).append("</code> from <code>")
                .append(REPORT_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(samples.get(0).capturedAtMs())))
                .append("</code> to <code>")
                .append(REPORT_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(samples.get(samples.size() - 1).capturedAtMs())))
                .append("</code>.\n\n");

        markdown.append("### Trend Summary\n\n");
        markdown.append("| Metric | First | Minimum | Maximum | Current | Delta |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: |\n");
        appendByteTrend(markdown, "Heap used", samples, ProfilerDiagnostics.DiagnosticSample::heapUsedBytes);
        appendByteTrend(markdown, "Heap after last GC", samples, ProfilerDiagnostics.DiagnosticSample::postGcHeapUsedBytes);
        appendByteTrend(markdown, "Old generation used", samples, ProfilerDiagnostics.DiagnosticSample::oldGenUsedBytes);
        appendByteTrend(markdown, "Old generation after last GC", samples, ProfilerDiagnostics.DiagnosticSample::oldGenPostGcUsedBytes);
        appendByteTrend(markdown, "Non-heap used", samples, ProfilerDiagnostics.DiagnosticSample::nonHeapUsedBytes);
        appendByteTrend(markdown, "Direct buffers", samples, ProfilerDiagnostics.DiagnosticSample::directBufferUsedBytes);
        appendLongTrend(markdown, "Live threads", samples, ProfilerDiagnostics.DiagnosticSample::liveThreads);
        appendLongTrend(markdown, "Loaded classes", samples, ProfilerDiagnostics.DiagnosticSample::loadedClasses);
        appendLongTrend(markdown, "GC collections", samples, ProfilerDiagnostics.DiagnosticSample::gcCollections);
        appendLongTrend(markdown, "GC time ms", samples, ProfilerDiagnostics.DiagnosticSample::gcTimeMs);
        appendLongTrend(markdown, "FPS", samples, sample -> sample.world().fps());
        appendLongTrend(markdown, "Loaded chunks", samples, sample -> sample.world().loadedChunks());
        appendLongTrend(markdown, "Entities", samples, sample -> sample.world().entities());
        appendLongTrend(markdown, "Block entities", samples, sample -> sample.world().blockEntities());
        appendLongTrend(markdown, "Particles", samples, sample -> sample.world().particles());
        appendLongTrend(markdown, "Textures", samples, sample -> sample.world().textures());
        appendLongTrend(markdown, "Map textures", samples, sample -> sample.world().mapTextures());
        appendLongTrend(markdown, "Active sounds", samples, sample -> sample.world().activeSounds());
        appendLongTrend(markdown, "FH fishing particles", samples, sample -> sample.frogHelper().fishingParticles());
        appendLongTrend(markdown, "FH alchemy spots", samples, sample -> sample.frogHelper().alchemySpots());
        appendLongTrend(markdown, "FH popup notifications", samples, sample -> sample.frogHelper().popupNotifications());
        appendLongTrend(markdown, "FH chat queue", samples, sample -> sample.frogHelper().chatQueue());
        appendLongTrend(markdown, "FH booster debug messages", samples, sample -> sample.frogHelper().boosterDebugMessages());
        appendLongTrend(markdown, "FH pending boss shares", samples, sample -> sample.frogHelper().pendingBossShares());
        appendLongTrend(markdown, "FH known mod users", samples, sample -> sample.frogHelper().knownModUsers());
        appendLongTrend(markdown, "FH social incoming buffers", samples, sample -> sample.frogHelper().socialIncomingBuffers());
        appendLongTrend(markdown, "FH clan entries", samples, sample -> sample.frogHelper().clanEntries());
        appendLongTrend(markdown, "FH highlight cached boxes", samples, sample -> sample.frogHelper().highlightCachedBoxes());
        appendLongTrend(markdown, "FH highlight cached chunks", samples, sample -> sample.frogHelper().highlightCachedChunks());
        appendLongTrend(markdown, "FH highlight dirty chunks", samples, sample -> sample.frogHelper().highlightDirtyChunks());
        appendLongTrend(markdown, "FH highlight dirty block positions", samples, sample -> sample.frogHelper().highlightDirtyBlockPositions());
        appendLongTrend(markdown, "FH highlight pending scans", samples, sample -> sample.frogHelper().highlightPendingChunkScans());
        markdown.append("\n");

        markdown.append("### Persistent Samples\n\n");
        markdown.append("| Time | Uptime | Sample Cost | Heap | Post-GC | Old Gen | Direct | GC Count | GC Time | CPU | FPS | Chunks | Entities | Block Entities | Particles | Textures | Map Textures | Sounds | Dimension | Position | Top Entities | Top Block Entities | FrogHelper Caches |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- | --- | --- |\n");
        for (ProfilerDiagnostics.DiagnosticSample sample : samples) {
            ProfilerDiagnostics.WorldSnapshot world = sample.world();
            markdown.append("| <code>").append(REPORT_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(sample.capturedAtMs())))
                    .append("</code> | ").append(formatDuration(sample.jvmUptimeMs()))
                    .append(" | ").append(formatMillis(sample.captureNanos())).append(" ms")
                    .append(" | ").append(formatMib(sample.heapUsedBytes()))
                    .append(" | ").append(formatMib(sample.postGcHeapUsedBytes()))
                    .append(" | ").append(formatMib(sample.oldGenUsedBytes()))
                    .append(" | ").append(formatMib(sample.directBufferUsedBytes()))
                    .append(" | ").append(sample.gcCollections())
                    .append(" | ").append(sample.gcTimeMs()).append(" ms")
                    .append(" | ").append(formatPercent(sample.processCpuLoad()))
                    .append(" | ").append(world.fps())
                    .append(" | ").append(world.loadedChunks())
                    .append(" | ").append(world.entities())
                    .append(" | ").append(world.blockEntities())
                    .append(" | ").append(world.particles())
                    .append(" | ").append(world.textures())
                    .append(" | ").append(world.mapTextures())
                    .append(" | ").append(world.activeSounds())
                    .append(" | <code>").append(escapePipe(world.dimension()))
                    .append("</code> | <code>").append(escapePipe(world.position()))
                    .append("</code> | <code>").append(escapePipe(formatTopTypes(world.topEntityTypes(), 5)))
                    .append("</code> | <code>").append(escapePipe(formatTopTypes(world.topBlockEntityTypes(), 5)))
                    .append("</code> | <code>").append(escapePipe(formatFrogHelperCaches(sample.frogHelper())))
                    .append("</code> |\n");
        }
        markdown.append("\n");
    }

    private void appendFullDiagnostics(StringBuilder markdown, ProfilerDiagnostics.FullDiagnostics diagnostics) {
        markdown.append("## Full Diagnostic Snapshot\n\n");
        if (diagnostics == null) {
            markdown.append("> Full diagnostics were unavailable.\n\n");
            return;
        }

        ProfilerDiagnostics.DiagnosticSample sample = diagnostics.sample();
        ProfilerDiagnostics.WorldSnapshot world = sample.world();
        markdown.append("| Environment | Value |\n");
        markdown.append("| --- | --- |\n");
        appendContextRow(markdown, "VM", diagnostics.vmName() + " " + diagnostics.vmVersion());
        appendContextRow(markdown, "Java", diagnostics.javaVersion());
        appendContextRow(markdown, "OS", diagnostics.operatingSystem());
        appendContextRow(markdown, "CPU", diagnostics.cpu());
        appendContextRow(markdown, "GPU vendor", diagnostics.gpuVendor());
        appendContextRow(markdown, "GPU renderer", diagnostics.gpuRenderer());
        appendContextRow(markdown, "OpenGL", diagnostics.openGlVersion());
        appendContextRow(markdown, "Diagnostic capture time", formatMillis(diagnostics.captureNanos()) + " ms");
        markdown.append("\n");

        markdown.append("### JVM and Process\n\n");
        markdown.append("| Metric | Value |\n");
        markdown.append("| --- | ---: |\n");
        appendContextRow(markdown, "Heap used / committed / max", formatMib(sample.heapUsedBytes()) + " / " + formatMib(sample.heapCommittedBytes()) + " / " + formatMib(sample.heapMaxBytes()));
        appendContextRow(markdown, "Heap after last GC", formatMib(sample.postGcHeapUsedBytes()));
        appendContextRow(markdown, "Old generation used", formatMib(sample.oldGenUsedBytes()));
        appendContextRow(markdown, "Old generation after last GC", formatMib(sample.oldGenPostGcUsedBytes()));
        appendContextRow(markdown, "Non-heap used / committed", formatMib(sample.nonHeapUsedBytes()) + " / " + formatMib(sample.nonHeapCommittedBytes()));
        appendContextRow(markdown, "Direct buffers", sample.directBufferCount() + " / " + formatMib(sample.directBufferUsedBytes()));
        appendContextRow(markdown, "Mapped buffers", sample.mappedBufferCount() + " / " + formatMib(sample.mappedBufferUsedBytes()));
        appendContextRow(markdown, "GC collections / time", sample.gcCollections() + " / " + sample.gcTimeMs() + " ms");
        appendContextRow(markdown, "Process CPU / system CPU", formatPercent(sample.processCpuLoad()) + " / " + formatPercent(sample.systemCpuLoad()));
        appendContextRow(markdown, "Process CPU time", formatDuration(sample.processCpuTimeNanos() / 1_000_000L));
        appendContextRow(markdown, "Threads live / daemon / peak", sample.liveThreads() + " / " + sample.daemonThreads() + " / " + sample.peakThreads());
        appendContextRow(markdown, "Classes loaded / total / unloaded", sample.loadedClasses() + " / " + sample.totalLoadedClasses() + " / " + sample.unloadedClasses());
        appendContextRow(markdown, "Deadlocked threads", diagnostics.deadlockedThreads().isEmpty() ? "none" : String.join(", ", diagnostics.deadlockedThreads()));
        markdown.append("\n");

        appendWorldSnapshot(markdown, world);
        appendFrogHelperSnapshot(markdown, sample.frogHelper());
        appendMemoryPools(markdown, diagnostics);
        appendClassHistogram(markdown, diagnostics);
        appendMods(markdown, diagnostics.mods());

        markdown.append("### JVM Arguments\n\n");
        if (diagnostics.jvmArguments().isEmpty()) {
            markdown.append("> No JVM arguments reported.\n\n");
        } else {
            markdown.append("```text\n");
            diagnostics.jvmArguments().forEach(argument -> markdown.append(argument).append("\n"));
            markdown.append("```\n\n");
        }
    }

    private void appendWorldSnapshot(StringBuilder markdown, ProfilerDiagnostics.WorldSnapshot world) {
        markdown.append("### Minecraft World State\n\n");
        markdown.append("| Metric | Value |\n");
        markdown.append("| --- | ---: |\n");
        appendContextRow(markdown, "Dimension", world.dimension());
        appendContextRow(markdown, "Screen", world.screen());
        appendContextRow(markdown, "Player position", world.position());
        appendContextRow(markdown, "FPS / frame time", world.fps() + " / " + formatMillis(world.frameTimeNanos()) + " ms");
        appendContextRow(markdown, "Render distance", Integer.toString(world.renderDistance()));
        appendContextRow(markdown, "Loaded chunks", Integer.toString(world.loadedChunks()));
        appendContextRow(markdown, "Entities / visible", world.entities() + " / " + world.visibleEntities());
        appendContextRow(markdown, "Block entities / global", world.blockEntities() + " / " + world.globalBlockEntities());
        appendContextRow(markdown, "Particles / pending / emitters", world.particles() + " / " + world.pendingParticles() + " / " + world.particleEmitters());
        appendContextRow(markdown, "Textures / tickable / maps", world.textures() + " / " + world.tickableTextures() + " / " + world.mapTextures());
        appendContextRow(markdown, "Sounds active / ticking / queued / deleting", world.activeSounds() + " / " + world.tickingSounds() + " / " + world.queuedSounds() + " / " + world.soundsPendingDeletion());
        markdown.append("\n");
        appendTypeCounts(markdown, "Top Entity Types", world.topEntityTypes());
        appendTypeCounts(markdown, "Top Block Entity Types", world.topBlockEntityTypes());
    }

    private void appendFrogHelperSnapshot(StringBuilder markdown, ProfilerDiagnostics.FrogHelperSnapshot state) {
        markdown.append("### FrogHelper Internal State\n\n");
        markdown.append("| Collection | Size |\n");
        markdown.append("| --- | ---: |\n");
        appendContextRow(markdown, "Fishing particles", Integer.toString(state.fishingParticles()));
        appendContextRow(markdown, "Alchemy spots", Integer.toString(state.alchemySpots()));
        appendContextRow(markdown, "Popup notifications", Integer.toString(state.popupNotifications()));
        appendContextRow(markdown, "Chat dispatch queue", Integer.toString(state.chatQueue()));
        appendContextRow(markdown, "Booster debug messages", Integer.toString(state.boosterDebugMessages()));
        appendContextRow(markdown, "Pending boss shares", Integer.toString(state.pendingBossShares()));
        appendContextRow(markdown, "Known mod users", Integer.toString(state.knownModUsers()));
        appendContextRow(markdown, "Social incoming / paired / acked", state.socialIncomingBuffers() + " / " + state.socialPairedPlayers() + " / " + state.socialAcknowledgedPlayers());
        appendContextRow(markdown, "Player clan entries", Integer.toString(state.clanEntries()));
        appendContextRow(markdown, "Highlight boxes block / entity / merged", state.highlightBlockBoxes() + " / " + state.highlightEntityBoxes() + " / " + state.highlightCachedBoxes());
        appendContextRow(markdown, "Highlight chunks cached / dirty / pending", state.highlightCachedChunks() + " / " + state.highlightDirtyChunks() + " / " + state.highlightPendingChunkScans());
        appendContextRow(markdown, "Highlight dirty block positions", Integer.toString(state.highlightDirtyBlockPositions()));
        markdown.append("\n");
    }

    private void appendMemoryPools(StringBuilder markdown, ProfilerDiagnostics.FullDiagnostics diagnostics) {
        markdown.append("### Memory Pools\n\n");
        markdown.append("| Pool | Type | Used | Committed | Max | After GC | Peak Used |\n");
        markdown.append("| --- | --- | ---: | ---: | ---: | ---: | ---: |\n");
        for (ProfilerDiagnostics.MemoryPoolView pool : diagnostics.memoryPools()) {
            markdown.append("| <code>").append(escapePipe(pool.name())).append("</code> | ")
                    .append(pool.type()).append(" | ")
                    .append(formatMib(pool.usage().used())).append(" | ")
                    .append(formatMib(pool.usage().committed())).append(" | ")
                    .append(formatMib(pool.usage().max())).append(" | ")
                    .append(formatMib(pool.collectionUsage().used())).append(" | ")
                    .append(formatMib(pool.peakUsed())).append(" |\n");
        }
        markdown.append("\n### Buffer Pools\n\n");
        markdown.append("| Pool | Count | Memory Used | Total Capacity |\n");
        markdown.append("| --- | ---: | ---: | ---: |\n");
        for (ProfilerDiagnostics.BufferPoolView pool : diagnostics.bufferPools()) {
            markdown.append("| <code>").append(escapePipe(pool.name())).append("</code> | ")
                    .append(pool.count()).append(" | ").append(formatMib(pool.memoryUsed()))
                    .append(" | ").append(formatMib(pool.totalCapacity())).append(" |\n");
        }
        markdown.append("\n### Garbage Collectors\n\n");
        markdown.append("| Collector | Collections | Time | Pools |\n");
        markdown.append("| --- | ---: | ---: | --- |\n");
        for (ProfilerDiagnostics.GcCollectorView collector : diagnostics.gcCollectors()) {
            markdown.append("| <code>").append(escapePipe(collector.name())).append("</code> | ")
                    .append(collector.collections()).append(" | ").append(collector.timeMs()).append(" ms | <code>")
                    .append(escapePipe(String.join(", ", collector.pools()))).append("</code> |\n");
        }
        markdown.append("\n");
        appendTypeCounts(markdown, "Thread States", diagnostics.threadStates());
    }

    private void appendClassHistogram(StringBuilder markdown, ProfilerDiagnostics.FullDiagnostics diagnostics) {
        markdown.append("### JVM Class Histogram\n\n");
        if (diagnostics.classHistogram().isEmpty()) {
            markdown.append("> Histogram unavailable: <code>")
                    .append(escapeHtml(diagnostics.histogramError())).append("</code>\n\n");
            return;
        }
        markdown.append("| Class | Instances | Bytes | MiB |\n");
        markdown.append("| --- | ---: | ---: | ---: |\n");
        for (ProfilerDiagnostics.HistogramEntry entry : diagnostics.classHistogram()) {
            markdown.append("| <code>").append(escapePipe(entry.className())).append("</code> | ")
                    .append(entry.instances()).append(" | ").append(entry.bytes()).append(" | ")
                    .append(String.format(Locale.ROOT, "%.2f", entry.bytes() / 1_048_576.0)).append(" |\n");
        }
        markdown.append("\n");
    }

    private void appendMods(StringBuilder markdown, List<ProfilerDiagnostics.ModView> mods) {
        markdown.append("### Loaded Mods\n\n");
        markdown.append("| Mod ID | Version | Name |\n");
        markdown.append("| --- | --- | --- |\n");
        for (ProfilerDiagnostics.ModView mod : mods) {
            markdown.append("| <code>").append(escapePipe(mod.id())).append("</code> | <code>")
                    .append(escapePipe(mod.version())).append("</code> | ")
                    .append(escapePipe(mod.name())).append(" |\n");
        }
        markdown.append("\n");
    }

    private void appendTypeCounts(StringBuilder markdown, String title, List<ProfilerDiagnostics.TypeCount> counts) {
        markdown.append("### ").append(title).append("\n\n");
        if (counts.isEmpty()) {
            markdown.append("> No data.\n\n");
            return;
        }
        markdown.append("| Type | Count |\n");
        markdown.append("| --- | ---: |\n");
        for (ProfilerDiagnostics.TypeCount count : counts) {
            markdown.append("| <code>").append(escapePipe(count.name())).append("</code> | ")
                    .append(count.count()).append(" |\n");
        }
        markdown.append("\n");
    }

    private void appendByteTrend(
            StringBuilder markdown,
            String name,
            List<ProfilerDiagnostics.DiagnosticSample> samples,
            ToLongFunction<ProfilerDiagnostics.DiagnosticSample> extractor
    ) {
        appendTrend(markdown, name, samples, extractor, true);
    }

    private void appendLongTrend(
            StringBuilder markdown,
            String name,
            List<ProfilerDiagnostics.DiagnosticSample> samples,
            ToLongFunction<ProfilerDiagnostics.DiagnosticSample> extractor
    ) {
        appendTrend(markdown, name, samples, extractor, false);
    }

    private void appendTrend(
            StringBuilder markdown,
            String name,
            List<ProfilerDiagnostics.DiagnosticSample> samples,
            ToLongFunction<ProfilerDiagnostics.DiagnosticSample> extractor,
            boolean bytes
    ) {
        long first = -1L;
        long current = -1L;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (ProfilerDiagnostics.DiagnosticSample sample : samples) {
            long value = extractor.applyAsLong(sample);
            if (value < 0L) {
                continue;
            }
            if (first < 0L) {
                first = value;
            }
            current = value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        String unavailable = "n/a";
        if (first < 0L) {
            markdown.append("| ").append(name).append(" | ").append(unavailable).append(" | ")
                    .append(unavailable).append(" | ").append(unavailable).append(" | ")
                    .append(unavailable).append(" | ").append(unavailable).append(" |\n");
            return;
        }
        markdown.append("| ").append(name).append(" | ")
                .append(formatTrendValue(first, bytes)).append(" | ")
                .append(formatTrendValue(min, bytes)).append(" | ")
                .append(formatTrendValue(max, bytes)).append(" | ")
                .append(formatTrendValue(current, bytes)).append(" | ")
                .append(formatSignedTrendValue(current - first, bytes)).append(" |\n");
    }

    private String formatTrendValue(long value, boolean bytes) {
        return bytes ? formatMib(value) : Long.toString(value);
    }

    private String formatSignedTrendValue(long value, boolean bytes) {
        String formatted = bytes ? formatMib(Math.abs(value)) : Long.toString(Math.abs(value));
        return (value >= 0L ? "+" : "-") + formatted;
    }

    private String formatPercent(double value) {
        return value < 0.0 ? "n/a" : String.format(Locale.ROOT, "%.1f%%", value * 100.0);
    }

    private String formatTopTypes(List<ProfilerDiagnostics.TypeCount> counts, int limit) {
        if (counts == null || counts.isEmpty()) {
            return "n/a";
        }
        return counts.stream()
                .limit(Math.max(1, limit))
                .map(count -> count.name() + "=" + count.count())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String formatFrogHelperCaches(ProfilerDiagnostics.FrogHelperSnapshot state) {
        return "fish=" + state.fishingParticles()
                + ", alchemy=" + state.alchemySpots()
                + ", popups=" + state.popupNotifications()
                + ", chatQ=" + state.chatQueue()
                + ", boosterDebug=" + state.boosterDebugMessages()
                + ", shares=" + state.pendingBossShares()
                + ", socialIn=" + state.socialIncomingBuffers()
                + ", highlightBoxes=" + state.highlightCachedBoxes()
                + ", highlightChunks=" + state.highlightCachedChunks()
                + ", highlightDirty=" + state.highlightDirtyBlockPositions();
    }

    private String formatDuration(long millis) {
        if (millis < 0L) {
            return "n/a";
        }
        long seconds = millis / 1_000L;
        return String.format(
                Locale.ROOT,
                "%02d:%02d:%02d",
                seconds / 3_600L,
                seconds / 60L % 60L,
                seconds % 60L
        );
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
            if (stall.diagnostics() != null) {
                ProfilerDiagnostics.DiagnosticSample diagnostic = stall.diagnostics();
                markdown.append("### Resources At Detection\n\n");
                markdown.append("| Heap | Post-GC Heap | Old Gen | Direct Buffers | GC Count | GC Time | Threads | Process CPU |\n");
                markdown.append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
                markdown.append("| ").append(formatMib(diagnostic.heapUsedBytes()))
                        .append(" | ").append(formatMib(diagnostic.postGcHeapUsedBytes()))
                        .append(" | ").append(formatMib(diagnostic.oldGenUsedBytes()))
                        .append(" | ").append(formatMib(diagnostic.directBufferUsedBytes()))
                        .append(" | ").append(diagnostic.gcCollections())
                        .append(" | ").append(diagnostic.gcTimeMs()).append(" ms")
                        .append(" | ").append(diagnostic.liveThreads())
                        .append(" | ").append(formatPercent(diagnostic.processCpuLoad()))
                        .append(" |\n\n");
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

    private void appendLifetimeTimeline(StringBuilder markdown, ReportSnapshot snapshot) {
        markdown.append("## Lifetime Connection Timeline\n\n");
        markdown.append("> This timeline is not cleared by `/fhprof reset` or `/fhprof start`.\n\n");
        if (snapshot.lifetimeTimeline().isEmpty()) {
            markdown.append("> No lifetime connection events were captured.\n\n");
            return;
        }

        markdown.append("| Time | JVM Uptime | Event | Detail |\n");
        markdown.append("| --- | ---: | --- | --- |\n");
        long generatedAt = snapshot.generatedAtMs();
        long currentUptime = snapshot.fullDiagnostics() != null
                ? snapshot.fullDiagnostics().sample().jvmUptimeMs()
                : 0L;
        for (TimelineEventView event : snapshot.lifetimeTimeline()) {
            long eventUptime = Math.max(0L, currentUptime - Math.max(0L, generatedAt - event.timestampMs()));
            markdown.append("| <code>").append(REPORT_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(event.timestampMs())))
                    .append("</code> | <code>").append(formatDuration(eventUptime))
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
                snapshot.persistentSamples(),
                snapshot.fullDiagnostics(),
                snapshot.lifetimeTimeline(),
                snapshot.lifetime(),
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
            List<ThreadStackView> threadStacks,
            ProfilerDiagnostics.DiagnosticSample diagnostics
    ) {
        public StallView(
                long startedAtMs,
                long detectedAtMs,
                long recoveredAtMs,
                long durationMs,
                boolean recovered,
                List<ThreadStackView> threadStacks
        ) {
            this(startedAtMs, detectedAtMs, recoveredAtMs, durationMs, recovered, threadStacks, null);
        }
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
            List<ProfilerDiagnostics.DiagnosticSample> persistentSamples,
            ProfilerDiagnostics.FullDiagnostics fullDiagnostics,
            List<TimelineEventView> lifetimeTimeline,
            LifetimeView lifetime,
            String focusPrefix
    ) {
        public ReportSnapshot(
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
            this(
                    enabled,
                    sessionStartedAtMs,
                    sessionStoppedAtMs,
                    sessionDurationMs,
                    generatedAtMs,
                    measuredNanos,
                    sections,
                    counters,
                    callTreeRoots,
                    samples,
                    context,
                    runtimeDiagnostics,
                    stalls,
                    timeline,
                    List.of(),
                    null,
                    List.of(),
                    new LifetimeView(0L, 0L, 0L, 0L, 0L),
                    focusPrefix
            );
        }
    }

    public record LifetimeView(
            long protocolPayloadCount,
            long protocolPayloadBytes,
            long joins,
            long disconnects,
            long dimensionChanges
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

    private static final class ActiveScope implements Scope {
        private final ModProfiler profiler;
        private final String section;
        private final long startedAtNanos;
        private final ActiveScope parent;
        private final Deque<ActiveScope> stack;
        private long childNanos;
        private boolean closed;

        private ActiveScope(
                ModProfiler profiler,
                String section,
                long startedAtNanos,
                ActiveScope parent,
                Deque<ActiveScope> stack
        ) {
            this.profiler = profiler;
            this.section = section;
            this.startedAtNanos = startedAtNanos;
            this.parent = parent;
            this.stack = stack;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            profiler.closeScope(this, stack);
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
        private ProfilerDiagnostics.DiagnosticSample diagnostics;

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
                    threadStacks,
                    diagnostics
            );
        }
    }

    private record TimelineEvent(long timestampMs, String event, String detail) {
    }
}
