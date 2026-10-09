package ru.wilyfox.client.profiler;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import ru.wilyfox.FrogHelper;
import ru.wilyfox.client.moduser.BackendSocialClient;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Event-triggered captures; file packaging and network work never run on the render thread. */
public final class AutomaticDiagnostics {
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(action -> {
        var thread = new Thread(action,"froghelper-diagnostics"); thread.setDaemon(true); return thread;
    });
    private final LowFpsCapturePolicy policy = new LowFpsCapturePolicy();
    private final AtomicBoolean capturing = new AtomicBoolean();
    private volatile boolean capacity = true, pending;
    private DiagnosticOutbox outbox;
    private boolean uploadInFlight;
    private static boolean registered;

    public static void register() {
        if (registered) return; registered = true;
        var service = new AutomaticDiagnostics();
        Path directory = Minecraft.getInstance().gameDirectory.toPath();
        String mod = FabricLoader.getInstance().getModContainer(FrogHelper.MOD_ID).orElseThrow().getMetadata().getVersion().getFriendlyString();
        String minecraft = SharedConstants.getCurrentVersion().name();
        service.worker.execute(() -> {
            try {
                service.outbox = new DiagnosticOutbox(directory,mod,minecraft);
                service.outbox.scanLatestCrash(Path.of(System.getProperty("user.dir")),Path.of(System.getProperty("java.io.tmpdir")));
                service.updateQueue();
            } catch (Exception failure) { service.capacity = false; FrogHelper.LOGGER.warn("Cannot initialize diagnostic outbox"); }
        });
        service.worker.scheduleWithFixedDelay(service::uploadNext,5,5,TimeUnit.SECONDS);
        ClientTickEvents.END_CLIENT_TICK.register(service::tick);
    }
    private void tick(Minecraft client) {
        long now = System.nanoTime() / 1_000_000;
        boolean active = client.level != null && client.player != null && client.isWindowActive() && !client.isPaused()
                && DiamondWorldProtocolClient.getGameToken() != null;
        considerCapture(client.getFps(),active,now);
    }
    void considerCapture(int fps,boolean active,long now) {
        if (!capacity || capturing.get() || !policy.shouldStart(now,fps,active)) return;
        if (!capturing.compareAndSet(false,true)) return;
        try {
            ModProfiler.getInstance().startAutomaticCapture(fps);
            FrogHelper.LOGGER.info("Automatic diagnostic capture started: {} FPS, 15 seconds",fps);
            worker.schedule(() -> finishCapture(fps),LowFpsCapturePolicy.CAPTURE_MILLIS,TimeUnit.MILLISECONDS);
        } catch (Exception failure) { capturing.set(false); FrogHelper.LOGGER.warn("Cannot start automatic diagnostic capture"); }
    }
    private void finishCapture(int fps) {
        try {
            ModProfiler.getInstance().finishAutomaticCapture().whenComplete((capture,failure) -> worker.execute(() -> {
                try {
                    if (failure != null || outbox == null) {
                        FrogHelper.LOGGER.warn("Automatic diagnostic capture could not be saved");
                        return;
                    }
                    outbox.enqueueProfile(capture,fps);
                    updateQueue();
                } catch (Exception error) {
                    FrogHelper.LOGGER.warn("Cannot package automatic diagnostic capture");
                } finally {
                    capturing.set(false);
                }
            }));
        } catch (Exception failure) {
            capturing.set(false);
            FrogHelper.LOGGER.warn("Cannot finish automatic diagnostic capture");
        }
    }
    private void updateQueue() throws Exception { pending = outbox.hasPending(); capacity = outbox.hasCapacity(); }
    private void uploadNext() {
        if (outbox == null || !pending || uploadInFlight || !BackendSocialClient.diagnosticAuthorizationReady()) return;
        try {
            var report = outbox.next(System.currentTimeMillis()); if (report == null) return;
            uploadInFlight = true;
            BackendSocialClient.uploadDiagnostic(report.path(),report.digest()).whenComplete((response,failure) -> worker.execute(() -> {
                try {
                    if (failure == null && (response.status() == 200 || response.status() == 202)) {
                        outbox.accepted(report); FrogHelper.LOGGER.info("Diagnostic accepted by backend: {} ({})",report.manifest().kind(),report.digest());
                    } else {
                        outbox.failed(report,System.currentTimeMillis(),failure == null ? response.retryAfterMillis() : 30_000);
                        FrogHelper.LOGGER.warn("Diagnostic upload deferred (HTTP {})",failure == null ? response.status() : 0);
                    }
                    updateQueue();
                } catch (Exception error) { FrogHelper.LOGGER.warn("Cannot update diagnostic outbox"); }
                finally { uploadInFlight = false; }
            }));
        } catch (Exception failure) { FrogHelper.LOGGER.warn("Cannot read pending diagnostic archive"); }
    }
}
