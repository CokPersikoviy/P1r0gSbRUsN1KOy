package ru.wilyfox.client.profiler;

import com.google.gson.Gson;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A bounded asynchronous journal plus independently readable report/JFR checkpoints.
 * Completed files survive Runtime.halt/native crashes without any shutdown callback. */
final class ProfilerCrashRecorder {
    private static final Gson JSON = new Gson();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final DateTimeFormatter SAVE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int QUEUE_LIMIT = 128;
    private static final int LINE_LIMIT = 256 * 1024;
    private static final long JOURNAL_LIMIT = 8 * 1024 * 1024;
    private static final long JFR_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5);
    private final Path directory;
    private final Map<String, ?> environment;
    private final Consumer<String> errors;
    private final boolean recordJfr;
    private final ScheduledExecutorService writer;
    private final Deque<Entry> pending = new ArrayDeque<>();
    private final List<CompletableFuture<Void>> flushes = new ArrayList<>();
    private final AtomicReference<Supplier<String>> checkpoint = new AtomicReference<>();
    private final AtomicBoolean immediateQueued = new AtomicBoolean();
    private final AtomicBoolean finishing = new AtomicBoolean();
    private final CompletableFuture<Void> finished = new CompletableFuture<>();
    private final Thread shutdownHook;
    private FileChannel journal;
    private Recording recording;
    private boolean initialized;
    private boolean jfrAttempted;
    private long dropped;
    private long lastJfrDump;
    private long lastErrorLog;
    private volatile long lastSavedAt;
    private volatile String lastError = "";

    ProfilerCrashRecorder(Path root, Map<String, ?> environment, boolean recordJfr, Consumer<String> errors) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        this.directory = root.toAbsolutePath().resolve("fhprof-live-" + stamp + "-"
                + ProcessHandle.current().pid() + "-" + SEQUENCE.incrementAndGet());
        this.environment = Map.copyOf(environment);
        this.recordJfr = recordJfr;
        this.errors = errors;
        writer = Executors.newSingleThreadScheduledExecutor(action -> {
            var thread = new Thread(action, "froghelper-profiler-save");
            thread.setDaemon(true);
            return thread;
        });
        shutdownHook = new Thread(() -> {
            try { finish().get(3, TimeUnit.SECONDS); } catch (Exception ignored) { }
        }, "froghelper-profiler-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        append("session/start", Map.of("directory", directory.toString()), false);
        writer.scheduleWithFixedDelay(this::saveSafely, 0, 1, TimeUnit.SECONDS);
    }

    Path directory() { return directory; }
    String status() {
        return directory + (lastSavedAt == 0 ? " (starting)" : " (saved " + SAVE_TIME.format(Instant.ofEpochMilli(lastSavedAt)) + ")")
                + (lastError.isEmpty() ? "" : " ERROR: " + lastError);
    }

    void append(String kind, Object data, boolean urgent) {
        if (finishing.get()) return;
        synchronized (pending) {
            if (pending.size() == QUEUE_LIMIT) { pending.removeFirst(); dropped++; }
            pending.addLast(new Entry(System.currentTimeMillis(), kind, data));
        }
        if (urgent) requestSave();
    }

    void checkpoint(Supplier<String> markdown) {
        if (!finishing.get()) checkpoint.set(markdown); // Only the newest snapshot can wait for I/O.
    }

    CompletableFuture<Void> flushAsync() {
        var result = new CompletableFuture<Void>();
        synchronized (pending) {
            if (finishing.get()) return finished;
            if (flushes.size() >= 16) {
                result.completeExceptionally(new IOException("Too many pending profiler flushes"));
                return result;
            }
            flushes.add(result);
        }
        requestSave();
        return result;
    }

    CompletableFuture<Void> finish() {
        synchronized (pending) {
            if (finishing.compareAndSet(false, true)) {
                pending.addLast(new Entry(System.currentTimeMillis(), "session/stop", Map.of()));
            }
        }
        requestSave();
        return finished;
    }

    private void requestSave() {
        if (!writer.isShutdown() && immediateQueued.compareAndSet(false, true)) {
            try {
                writer.execute(() -> {
                    try { saveSafely(); } finally { immediateQueued.set(false); }
                });
            } catch (RejectedExecutionException ignored) { immediateQueued.set(false); }
        }
    }

    private void saveSafely() {
        List<CompletableFuture<Void>> acknowledgements;
        boolean finalSave;
        synchronized (pending) {
            finalSave = finishing.get();
            acknowledgements = List.copyOf(flushes);
            flushes.clear();
        }
        Throwable failure = null;
        try {
            if (!initialized) {
                Files.createDirectories(directory);
                replaceFile(directory.resolve("environment.json"), path ->
                        Files.writeString(path, JSON.toJson(environment), StandardCharsets.UTF_8));
                initialized = true;
            }
            saveJournal(); // Durable data first, before JFR initialization or report formatting.
            Supplier<String> latest = checkpoint.getAndSet(null);
            if (latest != null) {
                try {
                    replaceFile(directory.resolve("report.md"), path -> Files.writeString(path,
                            "> Automatic crash checkpoint. Recent samples only; full events are in events.jsonl.\n\n"
                                    + latest.get(), StandardCharsets.UTF_8));
                } catch (Throwable exception) {
                    checkpoint.compareAndSet(null, latest);
                    reportFailure(exception);
                    failure = exception;
                }
            }
            startJfr();
            long now = System.nanoTime();
            if (recording != null && (finalSave || lastJfrDump == 0 || now - lastJfrDump >= JFR_INTERVAL_NANOS)) {
                try {
                    replaceFile(directory.resolve("profile.jfr"), recording::dump);
                    lastJfrDump = System.nanoTime();
                } catch (Throwable exception) { reportFailure(exception); failure = exception; }
            }
        } catch (Throwable exception) { reportFailure(exception); failure = exception; }
        for (var acknowledgement : acknowledgements) {
            if (failure == null) acknowledgement.complete(null);
            else acknowledgement.completeExceptionally(failure);
        }
        if (finalSave) {
            try {
                if (journal != null) journal.close();
                if (recording != null) recording.close();
            } catch (Throwable exception) { reportFailure(exception); failure = exception; }
            writer.shutdown();
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
            catch (IllegalStateException ignored) { }
            if (failure == null) finished.complete(null); else finished.completeExceptionally(failure);
        }
    }

    private void saveJournal() throws IOException {
        Path path = directory.resolve("events.jsonl");
        if (journal == null) journal = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        if (journal.size() >= JOURNAL_LIMIT) {
            journal.force(true);
            journal.close();
            journal = null;
            Files.move(path, directory.resolve("events.previous.jsonl"), StandardCopyOption.REPLACE_EXISTING);
            journal = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        }
        List<Entry> entries;
        synchronized (pending) {
            entries = new ArrayList<>(pending);
            pending.clear();
            if (dropped != 0) {
                entries.addFirst(new Entry(System.currentTimeMillis(), "journal/dropped", dropped));
                dropped = 0;
            }
        }
        entries.add(new Entry(System.currentTimeMillis(), "memory", memory()));
        for (Entry entry : entries) {
            String line = JSON.toJson(entry);
            if (line.length() > LINE_LIMIT) {
                line = JSON.toJson(new Entry(entry.timeMs, "journal/oversized", entry.kind));
            }
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(line + "\n");
            while (bytes.hasRemaining()) journal.write(bytes);
        }
        journal.force(true);
        lastSavedAt = System.currentTimeMillis();
    }

    private Map<String, Long> memory() {
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        var values = new LinkedHashMap<String, Long>();
        values.put("heapUsed", heap.getUsed());
        values.put("heapCommitted", heap.getCommitted());
        values.put("heapMax", heap.getMax());
        values.put("nonHeapUsed", nonHeap.getUsed());
        values.put("liveThreads", (long) ManagementFactory.getThreadMXBean().getThreadCount());
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            values.put("physicalFree", os.getFreeMemorySize());
            values.put("physicalTotal", os.getTotalMemorySize());
            values.put("swapFree", os.getFreeSwapSpaceSize());
            values.put("processCommittedVirtual", os.getCommittedVirtualMemorySize());
        }
        return values;
    }

    private void startJfr() {
        if (!recordJfr || jfrAttempted || finishing.get()) return;
        jfrAttempted = true;
        try {
            recording = new Recording(Configuration.getConfiguration("profile"));
            recording.setName("FrogHelper crash profile");
            recording.setMaxSize(32 * 1024 * 1024);
            recording.setMaxAge(Duration.ofMinutes(2));
            recording.setToDisk(true);
            recording.start();
        } catch (Throwable exception) {
            if (recording != null) { recording.close(); recording = null; }
            reportFailure(exception);
            append("jfr/unavailable", lastError, false);
        }
    }

    private void reportFailure(Throwable exception) {
        lastError = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        long now = System.nanoTime();
        if (lastErrorLog == 0 || now - lastErrorLog > TimeUnit.SECONDS.toNanos(30)) {
            lastErrorLog = now;
            try { errors.accept(lastError); } catch (Throwable ignored) { }
        }
    }

    /** Publish only after forcing the complete temporary file; a failed write preserves the old file. */
    static void replaceFile(Path destination, FileAction action) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), destination.getFileName() + ".", ".tmp");
        try {
            action.write(temporary);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    @FunctionalInterface interface FileAction { void write(Path file) throws IOException; }
    private record Entry(long timeMs, String kind, Object data) { }
}
