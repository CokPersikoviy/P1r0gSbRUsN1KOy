package ru.wilyfox.client.profiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticDiagnosticsTest {
    @TempDir Path temporary;
    @jdk.jfr.Name("fh.TestCaptureMarker")
    static final class Marker extends jdk.jfr.Event { String label; }
    @Test void filteredJfrKeepsWindowEventsAndOmitsStartupCredentials() throws Exception {
        Path source = temporary.resolve("source.jfr");
        try (var recording = new jdk.jfr.Recording()) {
            recording.enable("fh.TestCaptureMarker");
            recording.enable("jdk.JVMInformation").with("period","beginChunk");
            recording.enable("jdk.InitialSystemProperty").with("period","beginChunk");
            recording.start();
            var before = new Marker(); before.label="before"; before.commit();
            Thread.sleep(20);
            var inside = new Marker(); inside.label="inside"; inside.commit();
            Thread.sleep(20);
            Thread.sleep(20); var after=new Marker(); after.label="after"; after.commit();
            recording.stop(); recording.dump(source);
        }
        var sourceEvents = jdk.jfr.consumer.RecordingFile.readAllEvents(source);
        assertTrue(sourceEvents.stream().anyMatch(e -> e.getEventType().getName().equals("jdk.JVMInformation")));
        // Use the event clock itself: short wall-clock windows can drift against JFR on Windows.
        var insideEvent = sourceEvents.stream().filter(e -> e.getEventType().getName().equals("fh.TestCaptureMarker")
                && e.getString("label").equals("inside")).findFirst().orElseThrow();
        long start = insideEvent.getStartTime().toEpochMilli();
        var filtered = DiagnosticArchive.filterJfr(source,temporary,start,start+1);
        var events = jdk.jfr.consumer.RecordingFile.readAllEvents(filtered);
        assertTrue(events.stream().anyMatch(e -> e.getEventType().getName().equals("fh.TestCaptureMarker") && e.getString("label").equals("inside")));
        assertFalse(events.stream().anyMatch(e -> e.getEventType().getName().equals("jdk.JVMInformation") || e.getEventType().getName().equals("jdk.InitialSystemProperty")));
        assertFalse(events.stream().anyMatch(e -> e.getEventType().getName().equals("fh.TestCaptureMarker") && !e.getString("label").equals("inside")));
    }
    @Test void lowFpsRequiresFiveContinuousSecondsThenRecoveryAndCooldown() {
        var policy = new LowFpsCapturePolicy();
        assertFalse(policy.shouldStart(0,14,false));
        assertFalse(policy.shouldStart(0,15,true));
        assertFalse(policy.shouldStart(0,-1,true));
        assertFalse(policy.shouldStart(0,14,true));
        assertFalse(policy.shouldStart(4_999,0,true));
        assertTrue(policy.shouldStart(5_000,14,true));
        assertFalse(policy.shouldStart(610_000,0,true)); // A sustained drop stays one incident.
        assertFalse(policy.shouldStart(610_001,20,true));
        assertFalse(policy.shouldStart(615_001,20,true));
        assertFalse(policy.shouldStart(615_002,0,true));
        assertFalse(policy.shouldStart(620_001,0,true));
        assertTrue(policy.shouldStart(620_002,0,true));
        assertFalse(policy.shouldStart(625_000,20,true));
        assertFalse(policy.shouldStart(630_000,20,true));
        assertFalse(policy.shouldStart(630_001,14,true));
        assertFalse(policy.shouldStart(635_001,14,true)); // Recovery does not bypass the cooldown.
        assertEquals(15_000,LowFpsCapturePolicy.CAPTURE_MILLIS);
    }
    @Test void recoveryToFifteenFpsOrWindowDeactivationResetsConfirmation() {
        var policy = new LowFpsCapturePolicy();
        assertFalse(policy.shouldStart(0,14,true));
        assertFalse(policy.shouldStart(4_000,15,true));
        assertFalse(policy.shouldStart(5_000,14,true));
        assertFalse(policy.shouldStart(9_999,14,true));
        assertFalse(policy.shouldStart(10_000,14,false));
        assertFalse(policy.shouldStart(15_000,14,true));
        assertFalse(policy.shouldStart(19_999,14,true));
        assertTrue(policy.shouldStart(20_000,14,true));
    }
    @Test void invalidFpsResetsConfirmationAndMonotonicTimeCanHaveNegativeOrigin() {
        var policy = new LowFpsCapturePolicy();
        assertFalse(policy.shouldStart(-20_000,14,true));
        assertFalse(policy.shouldStart(-15_001,-1,true));
        assertFalse(policy.shouldStart(-15_000,14,true));
        assertFalse(policy.shouldStart(-10_001,14,true));
        assertTrue(policy.shouldStart(-10_000,14,true));
    }
    @Test void cooldownRequiresACompleteNewConfirmationWindow() {
        var policy = new LowFpsCapturePolicy();
        assertFalse(policy.shouldStart(0,14,true));
        assertTrue(policy.shouldStart(5_000,14,true));
        assertFalse(policy.shouldStart(10_000,20,true));
        assertFalse(policy.shouldStart(15_000,20,true));
        assertFalse(policy.shouldStart(604_000,14,true));
        assertFalse(policy.shouldStart(605_000,14,true));
        assertFalse(policy.shouldStart(609_999,14,true));
        assertTrue(policy.shouldStart(610_000,14,true));
    }
    @Test void noIncidentCreatesNoFiles() throws Exception {
        var outbox = new DiagnosticOutbox(temporary,"1.1.4","26.2");
        outbox.scanLatestCrash(temporary,temporary);
        assertFalse(outbox.hasPending());
        assertTrue(outbox.hasCapacity());
        assertFalse(Files.exists(outbox.root));
    }
    @Test void latestCrashSurvivesRestartAndIsOnlyAcceptedOnce() throws Exception {
        Path crashes = Files.createDirectories(temporary.resolve("crash-reports"));
        Files.writeString(crashes.resolve("crash-old.txt"),"old");
        Files.setLastModifiedTime(crashes.resolve("crash-old.txt"),FileTime.fromMillis(1));
        Files.writeString(crashes.resolve("crash-new.txt"),"Minecraft crash\n--accessToken secret-game-token\nstack trace");
        var outbox = new DiagnosticOutbox(temporary,"1.1.4","26.2");
        outbox.scanLatestCrash(temporary,temporary);
        var pending = outbox.next(System.currentTimeMillis());
        assertEquals("crash",pending.manifest().kind());
        try (var zip = new ZipFile(pending.path().toFile())) {
            String crash = new String(zip.getInputStream(zip.getEntry("crash.txt")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(crash.contains("stack trace")); assertFalse(crash.contains("secret-game-token"));
        }
        outbox.failed(pending,10_000,60_000);
        assertNull(outbox.next(69_999));
        assertNotNull(outbox.next(70_000));
        var restarted = new DiagnosticOutbox(temporary,"1.1.4","26.2");
        restarted.scanLatestCrash(temporary,temporary);
        assertEquals(1,restarted.pendingPaths().size());
        restarted.accepted(restarted.next(System.currentTimeMillis()));
        assertFalse(restarted.hasPending());
        var again = new DiagnosticOutbox(temporary,"1.1.4","26.2");
        again.scanLatestCrash(temporary,temporary);
        assertFalse(again.hasPending());
        assertTrue(Files.exists(crashes.resolve("crash-new.txt"))); // User's original crash log stays intact.
    }
    @Test void unrelatedNativeCrashIsIgnoredAndBrokenArchiveDoesNotBlockQueue() throws Exception {
        Path other = Files.createDirectories(temporary.resolve("other"));
        Files.writeString(other.resolve("hs_err_pid1.log"),"Minecraft --gameDir /another/instance");
        var outbox = new DiagnosticOutbox(temporary,"1.1.4","26.2");
        outbox.scanLatestCrash(other,null);
        assertFalse(outbox.hasPending());
        Files.createDirectories(outbox.root);
        Files.writeString(outbox.root.resolve("0".repeat(64)+".zip"),"broken zip");
        var manifest = new DiagnosticArchive.Manifest(1,"low-fps","1.1.4","26.2",1,15000,14,"","");
        var valid = DiagnosticArchive.create(outbox.root,manifest,Map.of("report.md","profile"),null);
        assertEquals(valid.digest(),outbox.next(10).digest());
        assertTrue(Files.exists(outbox.root.resolve("corrupt-archive.zip")));
    }
    @Test void archivesRedactCredentialsAndKeepStableDigestAcrossRetries() throws Exception {
        String text = "--accessToken game-secret Authorization: Bearer fake-bearer \"password\":\"db-secret\" DATABASE_URL=postgres://u:db@host/db DISCORD_ERROR_WEBHOOK_URL=https://discord.com/api/webhooks/123/secret-hook";
        var manifest = new DiagnosticArchive.Manifest(1,"low-fps","1.1.4","26.2",1,15000,0,"","");
        var pending = DiagnosticArchive.create(temporary,manifest,Map.of("report.md",text),null);
        assertEquals(pending.digest(),DiagnosticArchive.digest(pending.path()));
        assertEquals(pending,DiagnosticArchive.read(pending.path()));
        try (var zip = new ZipFile(pending.path().toFile())) {
            String report = new String(zip.getInputStream(zip.getEntry("report.md")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            for (String secret : new String[]{"game-secret","fake-bearer","db-secret","postgres://","secret-hook"}) assertFalse(report.contains(secret),report);
            assertTrue(report.contains("REDACTED"));
        }
    }
}
