package ru.wilyfox.client.profiler;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import ru.wilyfox.utils.AtomicFileWriter;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Explicit, short diagnostic phases. Idle code never scans the world or writes files. */
public final class DisplayProbe {
    private static final long SECOND = 1_000_000_000L;
    private static final long WARMUP = 2 * SECOND;
    private static final long DURATION = 20 * SECOND;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault());
    private static Session active;
    private static boolean registered;
    private static volatile Path lastSaved;
    private DisplayProbe() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Session session = active;
            if (session == null) return;
            long now = System.nanoTime();
            if (client.level != session.level) finish(client, "world_changed");
            else if (session.measurementStart != 0 && now - session.measurementStart >= DURATION) finish(client, "complete");
            else if (now - session.requestedAt >= 60 * SECOND) finish(client, "timeout");
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> finish(client, "disconnect"));
    }

    public static String start(Minecraft client, String label) {
        if (active != null) return "Display probe already running: " + active.label + ". Wait for the saved report.";
        if (client.level == null || client.player == null) return "Enter a world before starting a display probe.";
        if (label == null || !label.matches("[A-Za-z0-9_-]{1,32}")) return "Usage: /fhprof displays <front|back|front2|label> (1-32 letters, digits, _ or -).";
        active = new Session(label, client);
        ModProfiler.getInstance().recordClientEvent("display-probe/start", label);
        return "Display probe " + label + ": close chat and keep the camera still. 2s warmup + 20s recording; report saves automatically.";
    }

    public static String status() {
        return active != null ? "Display probe running: " + active.label
                : lastSaved != null ? "Last display report: " + lastSaved : "No display probe running.";
    }

    public static void onFrame(Minecraft client, Camera camera, Frustum frustum, LevelRenderState state) {
        Session session = active;
        if (session == null) return;
        if (client.level != session.level || client.player == null) { finish(client, "world_changed"); return; }
        if (client.gui.screen() != null || client.isPaused()) { session.lastFrameAt = 0; session.menuFrames++; return; }
        long now = System.nanoTime();
        if (session.measurementStart == 0) {
            session.measurementStart = now + WARMUP;
            session.nextSampleAt = session.measurementStart;
        }
        if (now < session.measurementStart) return;
        if (session.lastFrameAt != 0) { session.frameIntervals++; session.frameIntervalNanos += now - session.lastFrameAt; }
        session.lastFrameAt = now;
        if (!client.getWindow().isFocused()) session.unfocusedFrames++;
        if (now >= session.nextSampleAt && session.samples.size() < 24) {
            session.nextSampleAt = now + SECOND;
            try (var ignored = ModProfiler.getInstance().scope("diagnostic/displayProbe/sample")) {
                long started = System.nanoTime();
                Counts counts = count(client.level.entitiesForRendering(), state, frustum);
                session.samples.add(new Sample((now - session.measurementStart) / 1e9, client.getFps(),
                        client.level.players().size(), counts, camera.position().x, camera.position().y, camera.position().z,
                        camera.yRot(), camera.xRot(), camera.getFov(), client.getWindow().isFocused(),
                        ModProfiler.getInstance().isEnabled(), System.nanoTime() - started));
            }
        }
        if (now - session.measurementStart >= DURATION) finish(client, "complete");
    }

    static Counts count(Iterable<Entity> entities, LevelRenderState state, Frustum frustum) {
        int displays = 0, items = 0, noCull = 0, nominalOutside = 0, bone = 0, custom = 0, passengers = 0;
        Map<String, Integer> models = new HashMap<>();
        for (Entity entity : entities) {
            if (entity.isRemoved() || !(entity instanceof Display display)) continue;
            displays++;
            if (!(display instanceof Display.ItemDisplay item)) continue;
            items++;
            if (!display.affectedByCulling()) noCull++;
            if (frustum != null && !frustum.isVisible(display.getBoundingBoxForCulling())) nominalOutside++;
            if (entity.isPassenger()) passengers++;
            var stack = item.getItemStack();
            if (stack.is(Items.BONE)) bone++;
            if (stack.has(DataComponents.CUSTOM_MODEL_DATA)) custom++;
            var model = stack.get(DataComponents.ITEM_MODEL);
            String key = model != null ? model.toString() : String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            models.merge(key, 1, Integer::sum);
        }
        int prepared = 0, empty = 0;
        for (var entityState : state.entityRenderStates) {
            if (entityState instanceof ItemDisplayEntityRenderState itemState) {
                prepared++;
                if (itemState.item.isEmpty()) empty++;
            }
        }
        var top = models.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey())).limit(16).map(entry -> new ModelCount(entry.getKey(), entry.getValue())).toList();
        return new Counts(displays, items, noCull, nominalOutside, bone, custom, passengers, prepared, empty, top);
    }

    private static void finish(Minecraft client, String reason) {
        Session session = active;
        if (session == null) return;
        active = null;
        ModProfiler.getInstance().recordClientEvent("display-probe/end", session.label + ": " + reason);
        double fps = session.frameIntervalNanos > 0 ? session.frameIntervals * 1e9 / session.frameIntervalNanos : 0;
        String markdown = report(session.label, reason, session.context, fps, session.menuFrames, session.unfocusedFrames, List.copyOf(session.samples));
        Path output = client.gameDirectory.toPath().resolve("froghelper-profiler")
                .resolve("fhdisplays-" + FILE_TIME.format(Instant.now()) + "-" + session.label + ".md");
        CompletableFuture.runAsync(() -> {
            try {
                AtomicFileWriter.write(output, writer -> writer.write(markdown));
                lastSaved = output;
                client.execute(() -> message(client, String.format(Locale.ROOT, "%s finished (%s): %.1f FPS. Saved: %s", session.label, reason, fps, output)));
            } catch (Exception error) {
                ru.wilyfox.FrogHelper.LOGGER.warn("Could not save display probe", error);
                client.execute(() -> message(client, "Could not save display probe: " + error.getMessage()));
            }
        });
    }

    static String report(String label, String reason, String context, double fps, int menuFrames, int unfocusedFrames, List<Sample> samples) {
        var out = new StringBuilder("# Item Display probe\n\n");
        out.append("Phase: ").append(label).append("; result: ").append(reason).append("\n\n").append(context).append("\n\n");
        out.append(String.format(Locale.ROOT, "Mean observed FPS: %.2f\n\n", fps));
        out.append("Samples: ").append(samples.size()).append("; target measurement: 20 seconds after 2 seconds of warmup.\n\n");
        out.append("Menu frames excluded: ").append(menuFrames).append("; unfocused measured frames: ").append(unfocusedFrames).append("\n\n");
        out.append("Counts are sampled once per second. Prepared states are not a count of visible pixels.\n\n");
        out.append("**Outside nominal box** tests the server's culling AABB, which may be zero-sized or omit model translation/rotation/scale. It is a diagnostic indicator, not a safe decision to hide the model. Occlusion behind blocks is not measured.\n\n");
        out.append("| s | FPS | Players | Display loaded | Item loaded | Item no frustum culling | Item outside nominal box | Bone carriers | Custom model data | Passengers | Item prepared | Prepared empty | Camera XYZ | Yaw / pitch / FOV | Focused | Detailed profiler | Scan ms |\n");
        out.append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- | --- | ---: |\n");
        for (Sample sample : samples) {
            Counts c = sample.counts;
            out.append(String.format(Locale.ROOT, "| %.2f | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %.2f / %.2f / %.2f | %.2f / %.2f / %.2f | %s | %s | %.3f |\n",
                    sample.seconds, sample.fps, sample.players, c.displays, c.items, c.noCull, c.nominalOutside,
                    c.bone, c.custom, c.passengers, c.prepared, c.preparedEmpty, sample.x, sample.y, sample.z,
                    sample.yaw, sample.pitch, sample.fov, sample.focused, sample.profiling, sample.scanNanos / 1e6));
        }
        out.append("\n## Model identifiers by sample (top 16)\n\n");
        out.append("An identifier may select several models via CustomModelData; bone carriers alone do not identify decorative NPCs.\n\n");
        out.append("| s | Item model identifier | Count |\n| ---: | --- | ---: |\n");
        for (Sample sample : samples) for (ModelCount model : sample.counts.models)
            out.append(String.format(Locale.ROOT, "| %.2f | %s | %d |\n", sample.seconds, model.id.replace("|", "\\|"), model.count));
        return out.toString();
    }

    private static void message(Minecraft client, String text) {
        if (client.gui != null) client.gui.hud.getChat().addClientSystemMessage(Component.literal("[FH Profiler] " + text));
    }
    private static final class Session {
        final String label, context;
        final net.minecraft.client.multiplayer.ClientLevel level;
        final long requestedAt = System.nanoTime();
        final List<Sample> samples = new ArrayList<>(24);
        long measurementStart, nextSampleAt, lastFrameAt, frameIntervals, frameIntervalNanos;
        int menuFrames, unfocusedFrames;
        Session(String label, Minecraft client) {
            this.label = label; this.level = client.level;
            var config = ru.wilyfox.client.hud.config.ConfigManager.get();
            context = "Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().name()
                    + "; FrogHelper " + net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("froghelper").orElseThrow().getMetadata().getVersion().getFriendlyString()
                    + "; dimension " + level.dimension().identifier() + "; render distance " + client.options.getEffectiveRenderDistance()
                    + "; GUI scale " + client.getWindow().getGuiScale() + "; lightweight HUD " + config.render.lightweightHud
                    + "; native HUD " + config.render.nativeRenderer + "; detailed profiler " + ModProfiler.getInstance().isEnabled()
                    + "\n\nDW location " + ru.wilyfox.client.protocol.DiamondWorldProtocolClient.getCurrentGameLocation()
                    + "; server " + ru.wilyfox.client.protocol.DiamondWorldProtocolClient.getCurrentServerInfo();
        }
    }
    record ModelCount(String id, int count) {}
    record Counts(int displays, int items, int noCull, int nominalOutside, int bone, int custom, int passengers,
                  int prepared, int preparedEmpty, List<ModelCount> models) {}
    record Sample(double seconds, int fps, int players, Counts counts, double x, double y, double z,
                  float yaw, float pitch, float fov, boolean focused, boolean profiling, long scanNanos) {}
}
