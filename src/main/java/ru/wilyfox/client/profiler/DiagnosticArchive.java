package ru.wilyfox.client.profiler;

import com.google.gson.Gson;
import jdk.jfr.consumer.RecordingFile;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.*;

final class DiagnosticArchive {
    static final int MAX_ARCHIVE_BYTES = 8 << 20;
    static final int MAX_TEXT_BYTES = 2 << 20;
    static final Gson JSON = new Gson();
    record Manifest(int version, String kind, String modVersion, String minecraftVersion, long createdAtMs,
                    long durationMs, int triggerFps, String source, String sourceHash) {}
    record Pending(Path path, String digest, Manifest manifest) {}
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~-]+|eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern WEBHOOK = Pattern.compile("https://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api/(?:v[0-9]+/)?webhooks/[^\\s\\\"<>]+");
    private static final Pattern CREDENTIAL = Pattern.compile("(?i)((?:--|-D)?[A-Za-z0-9_.-]*(?:accessToken|gameToken|refreshToken|sessionToken|token|credential|authorization|password|secret|api[_-]?key|discord[_-]?(?:error[_-]?)?webhook[_-]?url|database[_-]?url)[\\\"']?\\s*(?:[:=]\\s*|\\s+)[\\\"']?)([^\\s\\\"',;<>]+)");
    static String redact(String text) {
        text = WEBHOOK.matcher(text).replaceAll("[REDACTED_WEBHOOK]");
        text = BEARER.matcher(text).replaceAll("[REDACTED_TOKEN]");
        return CREDENTIAL.matcher(text).replaceAll("$1[REDACTED]");
    }
    static String digest(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) { input.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(),digest)); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static String text(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_TEXT_BYTES + 1);
            String text = new String(bytes,0,Math.min(bytes.length,MAX_TEXT_BYTES),StandardCharsets.UTF_8);
            return redact(text) + (bytes.length > MAX_TEXT_BYTES ? "\n[Report truncated at 2 MiB]\n" : "");
        }
    }
    static Pending create(Path root, Manifest manifest, Map<String,String> textEntries, Path jfr) throws IOException {
        Files.createDirectories(root);
        Path temporary = Files.createTempFile(root,"archive-",".tmp");
        try {
            try (var raw = Files.newOutputStream(temporary); var limit = new CappedOutput(raw,MAX_ARCHIVE_BYTES); var zip = new ZipOutputStream(limit)) {
                put(zip,"manifest.json",JSON.toJson(manifest));
                for (var entry : textEntries.entrySet()) put(zip,entry.getKey(),redact(entry.getValue()));
                if (jfr != null && Files.isRegularFile(jfr) && Files.size(jfr) <= 32L << 20) {
                    zip.putNextEntry(new ZipEntry("profile.jfr"));
                    try (var input = Files.newInputStream(jfr)) { input.transferTo(zip); }
                    zip.closeEntry();
                }
            }
            try (var channel = FileChannel.open(temporary,StandardOpenOption.WRITE)) { channel.force(true); }
            String digest = digest(temporary);
            Path target = root.resolve(digest+".zip");
            move(temporary,target);
            return new Pending(target,digest,manifest);
        } catch (ArchiveTooLarge large) {
            if (jfr == null) throw large;
            var reduced = new java.util.LinkedHashMap<>(textEntries);
            reduced.put("notes.txt","JFR omitted because the compressed diagnostic archive exceeded 8 MiB.");
            return create(root,manifest,reduced,null);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void put(ZipOutputStream zip,String name,String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) bytes = java.util.Arrays.copyOf(bytes,MAX_TEXT_BYTES);
        zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry();
    }
    static Pending read(Path path) throws IOException {
        if (!path.getFileName().toString().matches("[a-f0-9]{64}\\.zip") || Files.size(path) > MAX_ARCHIVE_BYTES) throw new IOException("Invalid diagnostic archive");
        try (var zip = new ZipFile(path.toFile())) {
            var entry = zip.getEntry("manifest.json");
            if (entry == null || entry.getSize() > 8192) throw new IOException("Invalid diagnostic manifest");
            try (var input = zip.getInputStream(entry)) {
                Manifest manifest = JSON.fromJson(new String(input.readNBytes(8193),StandardCharsets.UTF_8),Manifest.class);
                if (manifest == null || manifest.version() != 1 || !("crash".equals(manifest.kind()) || "low-fps".equals(manifest.kind()))) throw new IOException("Invalid diagnostic manifest");
                return new Pending(path,path.getFileName().toString().substring(0,64),manifest);
            } catch (RuntimeException failure) { throw new IOException("Invalid diagnostic manifest"); }
        }
    }
    private static final java.util.Set<String> SENSITIVE_JFR_EVENTS = java.util.Set.of(
            "jdk.JVMInformation","jdk.InitialSystemProperty","jdk.InitialEnvironmentVariable",
            "jdk.InitialSecurityProperty","jdk.ProcessStart","jdk.SystemProcess");
    static Path filterJfr(Path source, Path directory, long start, long stop) throws IOException {
        if (!Files.isRegularFile(source)) return null;
        Path filtered = Files.createTempFile(directory,"window-",".jfr");
        Files.delete(filtered);
        try (var recording = new RecordingFile(source)) {
            Instant begin = Instant.ofEpochMilli(start), end = Instant.ofEpochMilli(stop);
            recording.write(filtered,event -> !SENSITIVE_JFR_EVENTS.contains(event.getEventType().getName()) && !event.getEndTime().isBefore(begin) && !event.getStartTime().isAfter(end));
            return filtered;
        } catch (IOException failure) { Files.deleteIfExists(filtered); throw failure; }
    }
    static void move(Path from,Path to) throws IOException {
        try { Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(from,to,StandardCopyOption.REPLACE_EXISTING); }
    }
    private static final class ArchiveTooLarge extends IOException { ArchiveTooLarge() { super("Diagnostic archive exceeds 8 MiB"); } }
    private static final class CappedOutput extends FilterOutputStream {
        private final long maximum; private long count;
        CappedOutput(OutputStream output,long maximum) { super(output); this.maximum = maximum; }
        @Override public void write(int value) throws IOException { if (++count > maximum) throw new ArchiveTooLarge(); out.write(value); }
        @Override public void write(byte[] data,int offset,int length) throws IOException { count += length; if (count > maximum) throw new ArchiveTooLarge(); out.write(data,offset,length); }
    }
}
