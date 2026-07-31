package ru.wilyfox.client.profiler;

import com.mojang.blaze3d.platform.GlUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import ru.wilyfox.client.alchemy.AlchemyIngredientTracker;
import ru.wilyfox.client.chat.BoosterChatDebug;
import ru.wilyfox.client.chat.BossShareService;
import ru.wilyfox.client.chat.ChatDispatchQueue;
import ru.wilyfox.client.clan.PlayerClanStorage;
import ru.wilyfox.client.highlight.UsefulWorldHighlightRenderHook;
import ru.wilyfox.client.hud.fishing.FishingSpotTracker;
import ru.wilyfox.client.moduser.ModUserProtocol;
import ru.wilyfox.client.moduser.ModUserStorage;
import ru.wilyfox.client.popup.PopUpManager;
import ru.wilyfox.mixin.LevelRendererAccessorMixin;
import ru.wilyfox.mixin.MapTextureManagerAccessorMixin;
import ru.wilyfox.mixin.ParticleEngineAccessorMixin;
import ru.wilyfox.mixin.SoundEngineAccessorMixin;
import ru.wilyfox.mixin.SoundManagerAccessorMixin;
import ru.wilyfox.mixin.TextureManagerAccessorMixin;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ProfilerDiagnostics {
    private static final int TYPE_SUMMARY_LIMIT = 12;
    private static final int HISTOGRAM_LIMIT = 512;

    private ProfilerDiagnostics() {
    }

    static DiagnosticSample captureSample(Minecraft minecraft) {
        long captureStartedNanos = System.nanoTime();
        long capturedAtMs = System.currentTimeMillis();
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();

        long postGcHeapUsed = 0L;
        boolean hasPostGcHeap = false;
        long oldGenUsed = -1L;
        long oldGenPostGcUsed = -1L;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) {
                continue;
            }
            MemoryUsage collection = pool.getCollectionUsage();
            if (collection != null && collection.getUsed() >= 0L) {
                postGcHeapUsed += collection.getUsed();
                hasPostGcHeap = true;
            }
            if (isOldGeneration(pool.getName())) {
                MemoryUsage usage = pool.getUsage();
                oldGenUsed = usage != null ? usage.getUsed() : -1L;
                oldGenPostGcUsed = collection != null ? collection.getUsed() : -1L;
            }
        }

        long gcCollections = 0L;
        long gcTimeMs = 0L;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCollections += nonNegative(collector.getCollectionCount());
            gcTimeMs += nonNegative(collector.getCollectionTime());
        }

        long directCount = -1L;
        long directUsed = -1L;
        long mappedCount = -1L;
        long mappedUsed = -1L;
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            String name = pool.getName().toLowerCase(Locale.ROOT);
            if (name.equals("direct")) {
                directCount = pool.getCount();
                directUsed = pool.getMemoryUsed();
            } else if (name.startsWith("mapped")) {
                mappedCount = Math.max(0L, mappedCount) + pool.getCount();
                mappedUsed = Math.max(0L, mappedUsed) + pool.getMemoryUsed();
            }
        }

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
        CpuSnapshot cpu = captureCpu();
        WorldSnapshot world = captureWorld(minecraft);
        FrogHelperSnapshot frogHelper = captureFrogHelper();

        return new DiagnosticSample(
                capturedAtMs,
                ManagementFactory.getRuntimeMXBean().getUptime(),
                heap.getUsed(),
                heap.getCommitted(),
                heap.getMax(),
                hasPostGcHeap ? postGcHeapUsed : -1L,
                oldGenUsed,
                oldGenPostGcUsed,
                nonHeap.getUsed(),
                nonHeap.getCommitted(),
                directCount,
                directUsed,
                mappedCount,
                mappedUsed,
                gcCollections,
                gcTimeMs,
                cpu.processCpuLoad(),
                cpu.systemCpuLoad(),
                cpu.processCpuTimeNanos(),
                threads.getThreadCount(),
                threads.getDaemonThreadCount(),
                threads.getPeakThreadCount(),
                classes.getLoadedClassCount(),
                classes.getTotalLoadedClassCount(),
                classes.getUnloadedClassCount(),
                Math.max(0L, System.nanoTime() - captureStartedNanos),
                world,
                frogHelper
        );
    }

    static FullDiagnostics captureFull(Minecraft minecraft, boolean includeHistogram) {
        long startedAt = System.nanoTime();
        DiagnosticSample sample = captureSample(minecraft);

        List<MemoryPoolView> memoryPools = new ArrayList<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            memoryPools.add(new MemoryPoolView(
                    pool.getName(),
                    pool.getType().name(),
                    usage(pool.getUsage()),
                    usage(pool.getCollectionUsage()),
                    pool.getPeakUsage() != null ? pool.getPeakUsage().getUsed() : -1L
            ));
        }

        List<BufferPoolView> bufferPools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
                .map(pool -> new BufferPoolView(pool.getName(), pool.getCount(), pool.getMemoryUsed(), pool.getTotalCapacity()))
                .toList();
        List<GcCollectorView> collectors = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(collector -> new GcCollectorView(
                        collector.getName(),
                        collector.getCollectionCount(),
                        collector.getCollectionTime(),
                        List.of(collector.getMemoryPoolNames())
                ))
                .toList();

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        Map<Thread.State, Integer> threadStates = new EnumMap<>(Thread.State.class);
        for (ThreadInfo info : threadBean.dumpAllThreads(false, false)) {
            if (info != null) {
                threadStates.merge(info.getThreadState(), 1, Integer::sum);
            }
        }
        long[] deadlockedIds = threadBean.findDeadlockedThreads();
        List<String> deadlockedThreads = new ArrayList<>();
        if (deadlockedIds != null) {
            for (ThreadInfo info : threadBean.getThreadInfo(deadlockedIds)) {
                if (info != null) {
                    deadlockedThreads.add(info.getThreadName());
                }
            }
        }

        List<ModView> mods = FabricLoader.getInstance().getAllMods().stream()
                .map(ProfilerDiagnostics::toModView)
                .sorted(Comparator.comparing(ModView::id))
                .toList();
        HistogramResult histogram = includeHistogram ? captureClassHistogram() : HistogramResult.empty();

        return new FullDiagnostics(
                sample,
                memoryPools,
                bufferPools,
                collectors,
                threadStates.entrySet().stream()
                        .map(entry -> new TypeCount(entry.getKey().name(), entry.getValue()))
                        .sorted(Comparator.comparing(TypeCount::name))
                        .toList(),
                List.copyOf(deadlockedThreads),
                List.copyOf(ManagementFactory.getRuntimeMXBean().getInputArguments()),
                mods,
                safeSystemProperty("java.vm.name"),
                safeSystemProperty("java.vm.version"),
                safeSystemProperty("java.version"),
                safeSystemProperty("os.name") + " " + safeSystemProperty("os.version") + " " + safeSystemProperty("os.arch"),
                safeGl(GlUtil::getCpuInfo),
                safeGl(GlUtil::getVendor),
                safeGl(GlUtil::getRenderer),
                safeGl(GlUtil::getOpenGLVersion),
                histogram.entries(),
                histogram.error(),
                Math.max(0L, System.nanoTime() - startedAt)
        );
    }

    private static WorldSnapshot captureWorld(Minecraft minecraft) {
        if (minecraft == null) {
            return WorldSnapshot.empty();
        }

        ClientLevel level = minecraft.level;
        String dimension = level != null ? level.dimension().location().toString() : "n/a";
        String screen = minecraft.screen != null ? minecraft.screen.getClass().getSimpleName() : "none";
        String position = minecraft.player != null
                ? formatPosition(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ())
                : "n/a";
        int fps = minecraft.getFps();
        long frameTimeNanos = minecraft.getFrameTimeNs();
        int renderDistance = minecraft.options.getEffectiveRenderDistance();

        int loadedChunks = -1;
        int entities = -1;
        int blockEntities = -1;
        int visibleEntities = -1;
        int globalBlockEntities = -1;
        List<TypeCount> entityTypes = List.of();
        List<TypeCount> blockEntityTypes = List.of();

        if (level != null) {
            loadedChunks = level.getChunkSource().getLoadedChunksCount();
            entities = level.getEntityCount();

            Map<String, Integer> entityCounts = new HashMap<>();
            for (Entity entity : level.entitiesForRendering()) {
                String key = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                entityCounts.merge(key, 1, Integer::sum);
            }
            entityTypes = topCounts(entityCounts);

            Map<String, Integer> blockEntityCounts = new HashMap<>();
            blockEntities = scanBlockEntities(level, renderDistance, blockEntityCounts);
            blockEntityTypes = topCounts(blockEntityCounts);
        }

        try {
            LevelRendererAccessorMixin renderer = (LevelRendererAccessorMixin) (LevelRenderer) minecraft.levelRenderer;
            visibleEntities = renderer.froghelper$getVisibleEntityCount();
            globalBlockEntities = renderer.froghelper$getGlobalBlockEntities().size();
        } catch (Throwable ignored) {
            // Optional diagnostic access must not affect the game.
        }

        int particles = -1;
        int pendingParticles = -1;
        int particleEmitters = -1;
        try {
            ParticleEngineAccessorMixin accessor = (ParticleEngineAccessorMixin) minecraft.particleEngine;
            particles = accessor.froghelper$getParticles().values().stream().mapToInt(queue -> queue.size()).sum();
            pendingParticles = accessor.froghelper$getParticlesToAdd().size();
            particleEmitters = accessor.froghelper$getTrackingEmitters().size();
        } catch (Throwable ignored) {
            // Optional diagnostic access must not affect the game.
        }

        int textures = -1;
        int tickableTextures = -1;
        int mapTextures = -1;
        try {
            TextureManagerAccessorMixin accessor = (TextureManagerAccessorMixin) minecraft.getTextureManager();
            textures = accessor.froghelper$getTextures().size();
            tickableTextures = accessor.froghelper$getTickableTextures().size();
            mapTextures = ((MapTextureManagerAccessorMixin) minecraft.getMapTextureManager()).froghelper$getMaps().size();
        } catch (Throwable ignored) {
            // Optional diagnostic access must not affect the game.
        }

        int activeSounds = -1;
        int tickingSounds = -1;
        int queuedSounds = -1;
        int soundsPendingDeletion = -1;
        try {
            SoundManager manager = minecraft.getSoundManager();
            SoundEngine engine = ((SoundManagerAccessorMixin) manager).froghelper$getSoundEngine();
            SoundEngineAccessorMixin accessor = (SoundEngineAccessorMixin) engine;
            activeSounds = accessor.froghelper$getInstanceToChannel().size();
            tickingSounds = accessor.froghelper$getTickingSounds().size();
            queuedSounds = accessor.froghelper$getQueuedSounds().size() + accessor.froghelper$getQueuedTickableSounds().size();
            soundsPendingDeletion = accessor.froghelper$getSoundDeleteTimes().size();
        } catch (Throwable ignored) {
            // Optional diagnostic access must not affect the game.
        }

        return new WorldSnapshot(
                dimension,
                screen,
                position,
                fps,
                frameTimeNanos,
                renderDistance,
                loadedChunks,
                entities,
                visibleEntities,
                blockEntities,
                globalBlockEntities,
                particles,
                pendingParticles,
                particleEmitters,
                textures,
                tickableTextures,
                mapTextures,
                activeSounds,
                tickingSounds,
                queuedSounds,
                soundsPendingDeletion,
                entityTypes,
                blockEntityTypes
        );
    }

    private static int scanBlockEntities(ClientLevel level, int renderDistance, Map<String, Integer> counts) {
        if (level == null || level.getChunkSource() == null) {
            return -1;
        }

        ClientChunkCache chunkSource = level.getChunkSource();
        ChunkPos center = level.getSharedSpawnPos() != null
                ? new ChunkPos(level.getSharedSpawnPos())
                : new ChunkPos(0, 0);
        if (Minecraft.getInstance().player != null) {
            center = Minecraft.getInstance().player.chunkPosition();
        }

        int radius = Math.max(2, Math.min(64, renderDistance + 2));
        int total = 0;
        for (int chunkX = center.x - radius; chunkX <= center.x + radius; chunkX++) {
            for (int chunkZ = center.z - radius; chunkZ <= center.z + radius; chunkZ++) {
                LevelChunk chunk = chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                total += chunk.getBlockEntities().size();
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    String key = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
                    counts.merge(key, 1, Integer::sum);
                }
            }
        }
        return total;
    }

    private static CpuSnapshot captureCpu() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean extended) {
            return new CpuSnapshot(
                    extended.getProcessCpuLoad(),
                    extended.getCpuLoad(),
                    extended.getProcessCpuTime()
            );
        }
        return new CpuSnapshot(-1.0, -1.0, -1L);
    }

    private static FrogHelperSnapshot captureFrogHelper() {
        try {
            ChatDispatchQueue.DebugSnapshot chatQueue = ChatDispatchQueue.getDebugSnapshot();
            ModUserProtocol.DebugSnapshot social = ModUserProtocol.diagnosticSnapshot();
            UsefulWorldHighlightRenderHook.DiagnosticSnapshot highlight =
                    UsefulWorldHighlightRenderHook.diagnosticSnapshot();
            return new FrogHelperSnapshot(
                    FishingSpotTracker.getInstance().diagnosticParticleCount(),
                    AlchemyIngredientTracker.getInstance().diagnosticSpotCount(),
                    PopUpManager.getInstance().diagnosticNotificationCount(),
                    chatQueue.size(),
                    BoosterChatDebug.diagnosticMessageCount(),
                    BossShareService.diagnosticPendingShareCount(),
                    ModUserStorage.knownCount(),
                    social.incomingBuffers(),
                    social.pairedPlayers(),
                    social.acknowledgedPlayers(),
                    PlayerClanStorage.diagnosticEntryCount(),
                    highlight.cachedBoxes(),
                    highlight.blockBoxes(),
                    highlight.entityBoxes(),
                    highlight.cachedChunks(),
                    highlight.dirtyChunks(),
                    highlight.dirtyBlockPositions(),
                    highlight.pendingChunkScans()
            );
        } catch (Throwable ignored) {
            return FrogHelperSnapshot.empty();
        }
    }

    private static HistogramResult captureClassHistogram() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("com.sun.management:type=DiagnosticCommand");
            Object result = server.invoke(
                    name,
                    "gcClassHistogram",
                    new Object[]{new String[]{"-all"}},
                    new String[]{"[Ljava.lang.String;"}
            );
            return parseHistogram(result instanceof String text ? text : "");
        } catch (Throwable throwable) {
            return new HistogramResult(List.of(), throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    static HistogramResult parseHistogram(String text) {
        if (text == null || text.isBlank()) {
            return new HistogramResult(List.of(), "empty histogram");
        }
        List<HistogramEntry> entries = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String[] parts = trimmed.substring(colon + 1).trim().split("\\s+", 3);
            if (parts.length < 3) {
                continue;
            }
            try {
                entries.add(new HistogramEntry(parts[2], Long.parseLong(parts[0]), Long.parseLong(parts[1])));
            } catch (NumberFormatException ignored) {
                // Header/footer lines are not histogram entries.
            }
        }
        entries.sort(Comparator.comparingLong(HistogramEntry::bytes).reversed());
        if (entries.size() > HISTOGRAM_LIMIT) {
            entries = new ArrayList<>(entries.subList(0, HISTOGRAM_LIMIT));
        }
        return new HistogramResult(List.copyOf(entries), entries.isEmpty() ? "no entries parsed" : "");
    }

    private static List<TypeCount> topCounts(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .map(entry -> new TypeCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(TypeCount::count).reversed().thenComparing(TypeCount::name))
                .limit(TYPE_SUMMARY_LIMIT)
                .toList();
    }

    private static ModView toModView(ModContainer container) {
        return new ModView(
                container.getMetadata().getId(),
                container.getMetadata().getVersion().getFriendlyString(),
                container.getMetadata().getName()
        );
    }

    private static UsageView usage(MemoryUsage usage) {
        return usage == null
                ? new UsageView(-1L, -1L, -1L, -1L)
                : new UsageView(usage.getInit(), usage.getUsed(), usage.getCommitted(), usage.getMax());
    }

    private static boolean isOldGeneration(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return normalized.contains("old") || normalized.contains("tenured");
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private static String formatPosition(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", x, y, z);
    }

    private static String safeSystemProperty(String key) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? "n/a" : value;
    }

    private static String safeGl(ValueSupplier supplier) {
        try {
            String value = supplier.get();
            return value == null || value.isBlank() ? "n/a" : value;
        } catch (Throwable ignored) {
            return "n/a";
        }
    }

    @FunctionalInterface
    private interface ValueSupplier {
        String get();
    }

    record DiagnosticSample(
            long capturedAtMs,
            long jvmUptimeMs,
            long heapUsedBytes,
            long heapCommittedBytes,
            long heapMaxBytes,
            long postGcHeapUsedBytes,
            long oldGenUsedBytes,
            long oldGenPostGcUsedBytes,
            long nonHeapUsedBytes,
            long nonHeapCommittedBytes,
            long directBufferCount,
            long directBufferUsedBytes,
            long mappedBufferCount,
            long mappedBufferUsedBytes,
            long gcCollections,
            long gcTimeMs,
            double processCpuLoad,
            double systemCpuLoad,
            long processCpuTimeNanos,
            int liveThreads,
            int daemonThreads,
            int peakThreads,
            int loadedClasses,
            long totalLoadedClasses,
            long unloadedClasses,
            long captureNanos,
            WorldSnapshot world,
            FrogHelperSnapshot frogHelper
    ) {
    }

    record WorldSnapshot(
            String dimension,
            String screen,
            String position,
            int fps,
            long frameTimeNanos,
            int renderDistance,
            int loadedChunks,
            int entities,
            int visibleEntities,
            int blockEntities,
            int globalBlockEntities,
            int particles,
            int pendingParticles,
            int particleEmitters,
            int textures,
            int tickableTextures,
            int mapTextures,
            int activeSounds,
            int tickingSounds,
            int queuedSounds,
            int soundsPendingDeletion,
            List<TypeCount> topEntityTypes,
            List<TypeCount> topBlockEntityTypes
    ) {
        static WorldSnapshot empty() {
            return new WorldSnapshot(
                    "n/a", "none", "n/a", -1, -1L, -1, -1, -1, -1, -1, -1,
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, List.of(), List.of()
            );
        }
    }

    record FullDiagnostics(
            DiagnosticSample sample,
            List<MemoryPoolView> memoryPools,
            List<BufferPoolView> bufferPools,
            List<GcCollectorView> gcCollectors,
            List<TypeCount> threadStates,
            List<String> deadlockedThreads,
            List<String> jvmArguments,
            List<ModView> mods,
            String vmName,
            String vmVersion,
            String javaVersion,
            String operatingSystem,
            String cpu,
            String gpuVendor,
            String gpuRenderer,
            String openGlVersion,
            List<HistogramEntry> classHistogram,
            String histogramError,
            long captureNanos
    ) {
    }

    record UsageView(long init, long used, long committed, long max) {
    }

    record MemoryPoolView(String name, String type, UsageView usage, UsageView collectionUsage, long peakUsed) {
    }

    record BufferPoolView(String name, long count, long memoryUsed, long totalCapacity) {
    }

    record GcCollectorView(String name, long collections, long timeMs, List<String> pools) {
    }

    record HistogramEntry(String className, long instances, long bytes) {
    }

    record HistogramResult(List<HistogramEntry> entries, String error) {
        static HistogramResult empty() {
            return new HistogramResult(List.of(), "");
        }
    }

    record TypeCount(String name, int count) {
    }

    record ModView(String id, String version, String name) {
    }

    record FrogHelperSnapshot(
            int fishingParticles,
            int alchemySpots,
            int popupNotifications,
            int chatQueue,
            int boosterDebugMessages,
            int pendingBossShares,
            int knownModUsers,
            int socialIncomingBuffers,
            int socialPairedPlayers,
            int socialAcknowledgedPlayers,
            int clanEntries,
            int highlightCachedBoxes,
            int highlightBlockBoxes,
            int highlightEntityBoxes,
            int highlightCachedChunks,
            int highlightDirtyChunks,
            int highlightDirtyBlockPositions,
            int highlightPendingChunkScans
    ) {
        static FrogHelperSnapshot empty() {
            return new FrogHelperSnapshot(
                    -1, -1, -1, -1, -1, -1, -1, -1, -1,
                    -1, -1, -1, -1, -1, -1, -1, -1, -1
            );
        }
    }

    private record CpuSnapshot(double processCpuLoad, double systemCpuLoad, long processCpuTimeNanos) {
    }
}
