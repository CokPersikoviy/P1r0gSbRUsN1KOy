package ru.wilyfox.client.profiler;

import ru.wilyfox.utils.AtomicFileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** All methods run on the diagnostic I/O thread, never the render thread. */
final class DiagnosticOutbox {
    static final int MAX_PENDING = 8;
    static final long MAX_PENDING_BYTES = 32L << 20;
    private final Path gameDirectory;
    final Path root;
    private final LinkedHashSet<String> crashReceipts = new LinkedHashSet<>();
    private final Map<String, Long> retryAt = new HashMap<>();
    private final Map<String, Integer> failures = new HashMap<>();
    private Path latestCrash;
    private String latestSource;
    private final String modVersion, minecraftVersion;

    DiagnosticOutbox(Path gameDirectory, String modVersion, String minecraftVersion) throws IOException {
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        this.root = this.gameDirectory.resolve("froghelper-profiler/diagnostics");
        this.modVersion = modVersion; this.minecraftVersion = minecraftVersion;
        Path receipts = root.resolve("crash-receipts.txt");
        if (Files.isRegularFile(receipts) && Files.size(receipts) <= 65536) {
            for (String digest : Files.readAllLines(receipts,StandardCharsets.UTF_8)) {
                if (digest.matches("[a-f0-9]{64}")) crashReceipts.add(digest);
            }
        }
    }
    List<Path> pendingPaths() throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (var paths = Files.list(root)) {
            return paths.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.zip") && Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                    .sorted().toList();
        }
    }
    boolean hasCapacity() throws IOException {
        var paths = pendingPaths(); long bytes = 0;
        for (Path path : paths) bytes += Files.size(path);
        return paths.size() < MAX_PENDING && bytes <= MAX_PENDING_BYTES - DiagnosticArchive.MAX_ARCHIVE_BYTES;
    }
    boolean hasPending() throws IOException { return !pendingPaths().isEmpty(); }
    private void chooseLatest(Path directory, String glob, String source, boolean external) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var paths = Files.newDirectoryStream(directory,glob)) {
            for (Path path : paths) {
                if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) continue;
                if (latestCrash != null && Files.getLastModifiedTime(path).compareTo(Files.getLastModifiedTime(latestCrash)) <= 0) continue;
                if (external) {
                    String text = DiagnosticArchive.text(path).toLowerCase(Locale.ROOT);
                    if (!text.contains("minecraft") && !text.contains("froghelper")) continue;
                    // Do not report a different launcher instance's native crash.
                    int gameDir = text.indexOf("--gamedir");
                    if (gameDir >= 0 && !text.substring(gameDir,Math.min(gameDir+512,text.length()))
                            .replace('\\','/').contains(gameDirectory.toString().replace('\\','/').toLowerCase(Locale.ROOT))) continue;
                }
                latestCrash = path; latestSource = source;
            }
        }
    }
    void scanLatestCrash(Path workingDirectory, Path temporaryDirectory) throws IOException {
        chooseLatest(gameDirectory.resolve("crash-reports"),"crash-*.txt","minecraft",false);
        chooseLatest(gameDirectory,"hs_err_pid*.log","jvm",false);
        if (workingDirectory != null && !workingDirectory.toAbsolutePath().normalize().equals(gameDirectory))
            chooseLatest(workingDirectory,"hs_err_pid*.log","jvm",true);
        if (temporaryDirectory != null && !temporaryDirectory.toAbsolutePath().normalize().equals(gameDirectory))
            chooseLatest(temporaryDirectory,"hs_err_pid*.log","jvm",true);
        enqueueLatestCrash();
    }
    void enqueueLatestCrash() throws IOException {
        if (latestCrash == null || !Files.isRegularFile(latestCrash,LinkOption.NOFOLLOW_LINKS) || !hasCapacity()) return;
        String text = DiagnosticArchive.text(latestCrash);
        String fingerprint;
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            hash.update(text.getBytes(StandardCharsets.UTF_8));
            hash.update((Files.size(latestCrash)+":"+Files.getLastModifiedTime(latestCrash).toMillis()).getBytes(StandardCharsets.UTF_8));
            fingerprint = HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        if (crashReceipts.contains(fingerprint)) return;
        for (Path path : pendingPaths()) {
            var queued = readPending(path);
            if (queued != null && fingerprint.equals(queued.manifest().sourceHash())) return;
        }
        var manifest = new DiagnosticArchive.Manifest(1,"crash",modVersion,minecraftVersion,Files.getLastModifiedTime(latestCrash).toMillis(),0,0,latestSource,fingerprint);
        DiagnosticArchive.create(root,manifest,Map.of("crash.txt",text),null);
    }
    void enqueueProfile(ModProfiler.AutomaticCapture capture, int fps) throws IOException {
        if (!hasCapacity()) throw new IOException("Diagnostic queue is full; raw capture retained");
        Files.createDirectories(root);
        Path jfr = null;
        var entries = new LinkedHashMap<String,String>();
        entries.put("report.md",capture.markdown());
        Path environment = capture.directory().resolve("environment.json");
        if (Files.isRegularFile(environment)) entries.put("environment.json",DiagnosticArchive.text(environment));
        try {
            try { jfr = DiagnosticArchive.filterJfr(capture.directory().resolve("profile.jfr"),root,capture.startedAtMs(),capture.finishedAtMs()); }
            catch (IOException failure) { entries.put("notes.txt","JFR unavailable; the profiler report is included."); }
            var manifest = new DiagnosticArchive.Manifest(1,"low-fps",modVersion,minecraftVersion,capture.startedAtMs(),
                    Math.max(15_000,Math.min(120_000,capture.finishedAtMs()-capture.startedAtMs())),fps,"","");
            DiagnosticArchive.create(root,manifest,entries,jfr);
        } finally {
            if (jfr != null) Files.deleteIfExists(jfr);
        }
        if (capture.ownsDirectory() && capture.directory().getParent().equals(gameDirectory.resolve("froghelper-profiler"))) {
            for (String name : List.of("environment.json","events.jsonl","events.previous.jsonl","report.md","profile.jfr"))
                Files.deleteIfExists(capture.directory().resolve(name));
            try { Files.delete(capture.directory()); } catch (DirectoryNotEmptyException ignored) { }
        }
    }
    private DiagnosticArchive.Pending readPending(Path path) throws IOException {
        try { return DiagnosticArchive.read(path); }
        catch (IOException invalid) {
            // A partial/corrupt archive must not block the other reports. Keep bounded evidence.
            DiagnosticArchive.move(path,root.resolve("corrupt-archive.zip"));
            return null;
        }
    }
    DiagnosticArchive.Pending next(long now) throws IOException {
        DiagnosticArchive.Pending next = null;
        for (Path path : pendingPaths()) {
            var candidate = readPending(path);
            if (candidate == null) continue;
            if (retryAt.getOrDefault(candidate.digest(),0L) > now) continue;
            if (next == null || candidate.manifest().kind().equals("crash") && !next.manifest().kind().equals("crash")
                    || candidate.manifest().kind().equals(next.manifest().kind()) && candidate.manifest().createdAtMs() < next.manifest().createdAtMs()) next = candidate;
        }
        return next;
    }
    void accepted(DiagnosticArchive.Pending report) throws IOException {
        String source = report.manifest().sourceHash();
        if (report.manifest().kind().equals("crash") && source != null) {
            var updated = new LinkedHashSet<>(crashReceipts); updated.add(source);
            while (updated.size() > 512) updated.remove(updated.iterator().next());
            AtomicFileWriter.write(root.resolve("crash-receipts.txt"),writer -> writer.write(String.join("\n",updated)+"\n"));
            crashReceipts.clear(); crashReceipts.addAll(updated);
        }
        Files.deleteIfExists(report.path());
        retryAt.remove(report.digest()); failures.remove(report.digest());
        enqueueLatestCrash();
    }
    void failed(DiagnosticArchive.Pending report,long now,long serverRetry) {
        int count = Math.min(5,failures.getOrDefault(report.digest(),0)+1); failures.put(report.digest(),count);
        retryAt.put(report.digest(),now+Math.max(serverRetry,Math.min(900_000,15_000L << count)));
    }
}
