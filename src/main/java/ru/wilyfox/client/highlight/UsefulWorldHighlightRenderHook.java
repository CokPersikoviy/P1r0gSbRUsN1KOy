package ru.wilyfox.client.highlight;

import com.mojang.authlib.properties.Property;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.PlayerHeadBlock;
import net.minecraft.world.level.block.PlayerWallHeadBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.popup.PopUpManager;
import ru.wilyfox.client.popup.PopUpRequest;
import ru.wilyfox.client.popup.PopUpSeverity;
import ru.wilyfox.client.popup.PopUpSource;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.protocol.CurrentServerInfo;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class UsefulWorldHighlightRenderHook {
    private static final String PROFILER_PREFIX = "render/UsefulWorldHighlightRenderHook";
    private static final int GOLDEN_CRYSTAL_MODEL_ID = 271;
    private static final int ENTITY_SCAN_INTERVAL_TICKS = 10;
    private static final int BLOCK_SCAN_REFRESH_TICKS = 100;
    private static final long WORLD_CONTEXT_WAIT_NANOS = 1_000_000_000L;
    private static final int DIRTY_CHUNK_RESCAN_LIMIT = 2;
    private static final int DIRTY_LOCAL_RESCAN_RADIUS = 1;
    private static final int DIRTY_FULL_RESCAN_THRESHOLD = 6;
    private static final int MAX_BLOCK_SCAN_STEPS_PER_FRAME = 8192;
    private static final long TARGET_CHUNK_SCAN_BUDGET_NANOS = 1_500_000L;
    private static final long DIRTY_CHUNK_MARK_COOLDOWN_MS = 500L;
    private static final int HORIZONTAL_SCAN_RADIUS = 32;
    private static final int VERTICAL_SCAN_RADIUS = 20;
    private static final double GOLDEN_CRYSTAL_CLUSTER_RADIUS = 1.6D;
    private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {
            net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
    private static final double GOLDEN_CRYSTAL_BOX_WIDTH = 0.58D;
    private static final double GOLDEN_CRYSTAL_BOX_HEIGHT = 0.46D;
    private static final double GOLDEN_CRYSTAL_BOX_Y_OFFSET = 0.28D;

    private static final List<ColoredBox> CACHED_BOXES = new ArrayList<>();
    private static final List<ColoredBox> BLOCK_BOXES = new ArrayList<>();
    private static final List<ColoredBox> ENTITY_BOXES = new ArrayList<>();
    private static final GoldenCrystalDiscoveryTracker GOLDEN_CRYSTAL_TRACKER =
            new GoldenCrystalDiscoveryTracker();
    private static final Map<Long, ChunkScanResult> BLOCK_CHUNK_CACHE = new HashMap<>();
    private static final Map<Long, Long> DIRTY_CHUNK_MARK_TIMES = new HashMap<>();
    private static final Set<Long> DIRTY_CHUNK_KEYS = new HashSet<>();
    private static final Map<Long, Set<Long>> DIRTY_BLOCK_POSITIONS = new HashMap<>();
    private static final LinkedHashSet<Long> PENDING_BLOCK_SCAN_KEYS = new LinkedHashSet<>();
    private static long lastEntityScanTick = Long.MIN_VALUE;
    private static long lastBlockRefreshTick = Long.MIN_VALUE;
    private static final Map<Long, ChunkScanCursor> BLOCK_SCAN_CURSORS = new HashMap<>();
    private static int lastMinChunkX = Integer.MIN_VALUE;
    private static int lastMaxChunkX = Integer.MIN_VALUE;
    private static int lastMinChunkZ = Integer.MIN_VALUE;
    private static int lastMaxChunkZ = Integer.MIN_VALUE;
    private static boolean waitingForWorldContext;
    private static long gameLocationRevisionAtTransition;
    private static long worldContextWaitStartedAt;
    private static boolean entityScanDirty = true;
    private static BlockPos lastScanCenter;
    private static CurrentServerInfo lastServerInfo;
    private static String lastGameLocation;
    private static ClientLevel cachedLevel;
    private static boolean registered;

    private UsefulWorldHighlightRenderHook() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        LevelRenderEvents.BEFORE_GIZMOS.register(UsefulWorldHighlightRenderHook::onAfterEntities);
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            Minecraft mc = Minecraft.getInstance();
            if (level != mc.level || mc.player == null || !needsBlockDetection()) return;
            BlockPos center = mc.player.blockPosition();
            ChunkPos pos = chunk.getPos();
            if (pos.x() >= SectionPos.blockToSectionCoord(center.getX() - HORIZONTAL_SCAN_RADIUS)
                    && pos.x() <= SectionPos.blockToSectionCoord(center.getX() + HORIZONTAL_SCAN_RADIUS)
                    && pos.z() >= SectionPos.blockToSectionCoord(center.getZ() - HORIZONTAL_SCAN_RADIUS)
                    && pos.z() <= SectionPos.blockToSectionCoord(center.getZ() + HORIZONTAL_SCAN_RADIUS)) {
                PENDING_BLOCK_SCAN_KEYS.add(pos.pack());
            }
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            if (level != Minecraft.getInstance().level) return;
            long key = chunk.getPos().pack();
            BLOCK_CHUNK_CACHE.remove(key);
            PENDING_BLOCK_SCAN_KEYS.remove(key);
            BLOCK_SCAN_CURSORS.remove(key);
            DIRTY_CHUNK_KEYS.remove(key);
            DIRTY_BLOCK_POSITIONS.remove(key);
            DIRTY_CHUNK_MARK_TIMES.remove(key);
            rebuildBlockBoxes();
        });
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> markEntityDirty(entity));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> markEntityDirty(entity));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clearCache();
            GOLDEN_CRYSTAL_TRACKER.clear();
            cachedLevel = null;
            lastServerInfo = null;
            lastGameLocation = null;
            waitingForWorldContext = false;
            gameLocationRevisionAtTransition = 0L;
        });
    }

    public static void onPlayerTeleport() {
        PopUpManager.getInstance().removeSources(PopUpSource.BARREL_FOUND, PopUpSource.GOLDEN_CRYSTAL_FOUND);
        waitingForWorldContext = true;
        gameLocationRevisionAtTransition = DiamondWorldProtocolClient.getGameLocationRevision();
        worldContextWaitStartedAt = System.nanoTime();
        clearCache();
        count("worldContext/transition");
    }

    public static void markEntityDirty(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) markEntityDirty(mc.level.getEntity(entityId));
    }

    private static void markEntityDirty(Entity entity) {
        if (entity != null && isUsefulHighlightEntity(entity)) entityScanDirty = true;
    }

    public static void markBlockDirty(BlockPos blockPos) {
        try (ModProfiler.Scope ignored = profile("dirtyMark")) {
            if (blockPos == null) {
                count("dirtyMark/skippedNull");
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                count("dirtyMark/skippedNoWorld");
                return;
            }

            if (waitingForWorldContext) {
                count("dirtyMark/skippedWorldTransition");
                return;
            }

            if (!needsBlockDetection() || !isMineHighlightLocation()) {
                count("dirtyMark/skippedDisabled");
                return;
            }

            long chunkKey = ChunkPos.pack(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
            DIRTY_CHUNK_KEYS.add(chunkKey);
            BLOCK_SCAN_CURSORS.remove(chunkKey);
            Set<Long> dirtyPositions = DIRTY_BLOCK_POSITIONS.computeIfAbsent(chunkKey, unused -> new LinkedHashSet<>());
            boolean added = dirtyPositions.add(blockPos.asLong());
            count(added ? "dirtyMark/accepted" : "dirtyMark/duplicate");
            count("dirtyMark/chunkDirtyPositions", dirtyPositions.size());
            long now = System.currentTimeMillis();
            Long lastMarkedAt = DIRTY_CHUNK_MARK_TIMES.get(chunkKey);
            if (lastMarkedAt != null && now - lastMarkedAt < DIRTY_CHUNK_MARK_COOLDOWN_MS) {
                count("dirtyMark/fullScanThrottleWindow");
                return;
            }

            DIRTY_CHUNK_MARK_TIMES.put(chunkKey, now);
        }
    }

    public static DiagnosticSnapshot diagnosticSnapshot() {
        int dirtyBlockPositions = DIRTY_BLOCK_POSITIONS.values().stream().mapToInt(Set::size).sum();
        return new DiagnosticSnapshot(
                CACHED_BOXES.size(),
                BLOCK_BOXES.size(),
                ENTITY_BOXES.size(),
                BLOCK_CHUNK_CACHE.size(),
                DIRTY_CHUNK_KEYS.size(),
                dirtyBlockPositions,
                PENDING_BLOCK_SCAN_KEYS.size()
        );
    }

    private static void onAfterEntities(LevelRenderContext context) {
        try (ModProfiler.Scope ignored = profile("frame")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                count("frame/skippedNoContext");
                clearCache();
                return;
            }

            if (cachedLevel != mc.level) {
                clearCache();
                cachedLevel = mc.level;
            }

            if (!needsDetection()) {
                count("frame/skippedDisabled");
                clearCache();
                return;
            }

            if (shouldWaitForWorldContext()) {
                count("frame/skippedWorldTransition");
                return;
            }

            refreshCacheIfNeeded(mc);
            if (!ConfigManager.get().render.usefulItemsHighlight || CACHED_BOXES.isEmpty()) {
                count("frame/skippedEmptyBoxes");
                return;
            }

            count("frame/cachedBoxes", CACHED_BOXES.size());
            try (var collection = context.levelRenderer().collectPerFrameRenderThreadGizmos();
                 ModProfiler.Scope drawScope = profile("drawBoxes")) {
                for (ColoredBox coloredBox : CACHED_BOXES) {
                    int color = 0xFF000000 | (Math.round(coloredBox.red * 255) << 16)
                            | (Math.round(coloredBox.green * 255) << 8) | Math.round(coloredBox.blue * 255);
                    Gizmos.cuboid(coloredBox.box, GizmoStyle.stroke(color, 2.0F)).setAlwaysOnTop();
                }
            }
        }
    }

    private static void refreshCacheIfNeeded(Minecraft mc) {
        try (ModProfiler.Scope ignored = profile("refreshCache")) {
            if (needsBlockDetection()) refreshBlockCacheIfNeeded(mc);
            else { BLOCK_BOXES.clear(); BLOCK_CHUNK_CACHE.clear(); PENDING_BLOCK_SCAN_KEYS.clear(); BLOCK_SCAN_CURSORS.clear(); DIRTY_CHUNK_KEYS.clear(); DIRTY_BLOCK_POSITIONS.clear(); DIRTY_CHUNK_MARK_TIMES.clear(); NearbyMineState.clearBarrels(); lastBlockRefreshTick=Long.MIN_VALUE; lastScanCenter=null; }
            if (ConfigManager.get().render.usefulItemsHighlight || counterAdded(ru.wilyfox.client.hud.config.WidgetCatalog.NEARBY_WORMS)
                    || counterAdded(ru.wilyfox.client.hud.config.WidgetCatalog.NEARBY_CRYSTALS)) refreshEntityCacheIfNeeded(mc);
            else { ENTITY_BOXES.clear(); NearbyMineState.clearEntities(); lastEntityScanTick=Long.MIN_VALUE; }

            try (ModProfiler.Scope mergeScope = profile("refreshCache/merge")) {
                CACHED_BOXES.clear();
                CACHED_BOXES.addAll(BLOCK_BOXES);
                CACHED_BOXES.addAll(ENTITY_BOXES);
                count("refreshCache/blockBoxes", BLOCK_BOXES.size());
                count("refreshCache/entityBoxes", ENTITY_BOXES.size());
                count("refreshCache/mergedBoxes", CACHED_BOXES.size());
            }
        }
    }

    private static boolean shouldWaitForWorldContext() {
        long locationRevision = DiamondWorldProtocolClient.getGameLocationRevision();
        CurrentServerInfo serverInfo = DiamondWorldProtocolClient.getCurrentServerInfo();
        String gameLocation = DiamondWorldProtocolClient.getCurrentGameLocation();
        boolean contextChanged = lastServerInfo != null
                && (!Objects.equals(lastServerInfo, serverInfo) || !Objects.equals(lastGameLocation, gameLocation));
        lastServerInfo = serverInfo;
        lastGameLocation = gameLocation;

        if (contextChanged) {
            clearCache();
            GOLDEN_CRYSTAL_TRACKER.clear();
            count("worldContext/contextChanged");
        }

        if (!waitingForWorldContext) {
            return false;
        }

        // Teleports within one mine do not necessarily produce a new game-location packet.
        if (locationRevision != gameLocationRevisionAtTransition || contextChanged
                || System.nanoTime() - worldContextWaitStartedAt >= WORLD_CONTEXT_WAIT_NANOS) {
            waitingForWorldContext = false;
            clearCache();
            count(locationRevision != gameLocationRevisionAtTransition
                    ? "worldContext/locationPacketReceived" : "worldContext/waitReleasedWithoutLocationPacket");
            return false;
        }

        return true;
    }

    private static void refreshBlockCacheIfNeeded(Minecraft mc) {
        try (ModProfiler.Scope ignored = profile("refreshBlockCache")) {
            if (!isMineHighlightLocation()) {
                count("refreshBlockCache/skippedNotMine");
                BLOCK_BOXES.clear();
                NearbyMineState.clearBarrels();
                BLOCK_CHUNK_CACHE.clear();
                DIRTY_CHUNK_MARK_TIMES.clear();
                DIRTY_CHUNK_KEYS.clear();
                DIRTY_BLOCK_POSITIONS.clear();
                PENDING_BLOCK_SCAN_KEYS.clear(); BLOCK_SCAN_CURSORS.clear();
                lastBlockRefreshTick = mc.level.getGameTime();
                return;
            }

            long gameTime = mc.level.getGameTime();
            BlockPos playerPos = mc.player.blockPosition();
            int minChunkX = SectionPos.blockToSectionCoord(playerPos.getX() - HORIZONTAL_SCAN_RADIUS);
            int maxChunkX = SectionPos.blockToSectionCoord(playerPos.getX() + HORIZONTAL_SCAN_RADIUS);
            int minChunkZ = SectionPos.blockToSectionCoord(playerPos.getZ() - HORIZONTAL_SCAN_RADIUS);
            int maxChunkZ = SectionPos.blockToSectionCoord(playerPos.getZ() + HORIZONTAL_SCAN_RADIUS);
            int chunkWindowSize = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
            count("refreshBlockCache/chunkWindowSize", chunkWindowSize);

            boolean chunkWindowChanged = minChunkX != lastMinChunkX
                    || maxChunkX != lastMaxChunkX
                    || minChunkZ != lastMinChunkZ
                    || maxChunkZ != lastMaxChunkZ
                    || lastScanCenter == null
                    || Math.abs(playerPos.getY() - lastScanCenter.getY()) >= 2
                    || Math.abs(playerPos.getX() - lastScanCenter.getX()) >= 4
                    || Math.abs(playerPos.getZ() - lastScanCenter.getZ()) >= 4;
            boolean refreshExpired = lastBlockRefreshTick == Long.MIN_VALUE
                    || gameTime < lastBlockRefreshTick
                    || gameTime - lastBlockRefreshTick >= BLOCK_SCAN_REFRESH_TICKS;
            // PENDING_BLOCK_SCAN_KEYS empty means the current window has been fully scanned at
            // least once - unlike BLOCK_BOXES.isEmpty(), this stays true even when the scan
            // legitimately found nothing nearby, so the fast path doesn't get skipped every frame.
            if (!chunkWindowChanged && !refreshExpired && PENDING_BLOCK_SCAN_KEYS.isEmpty()) {
                count("refreshBlockCache/fastPathHits");
                processDirtyChunks(mc, minChunkX, maxChunkX, minChunkZ, maxChunkZ);
                if (processPendingChunkScans(mc)) rebuildBlockBoxes();
                return;
            }

            count(chunkWindowChanged ? "refreshBlockCache/windowChanged" : "refreshBlockCache/windowStable");
            count(refreshExpired ? "refreshBlockCache/refreshExpired" : "refreshBlockCache/refreshFresh");

            lastBlockRefreshTick = gameTime;
            lastMinChunkX = minChunkX;
            lastMaxChunkX = maxChunkX;
            lastMinChunkZ = minChunkZ;
            lastMaxChunkZ = maxChunkZ;
            if (chunkWindowChanged || refreshExpired) lastScanCenter = playerPos;

            if (chunkWindowChanged) BLOCK_SCAN_CURSORS.clear();
            Set<Long> activeChunkKeys = new HashSet<>();
            int newlyQueuedChunks = 0;
            try (ModProfiler.Scope buildWindowScope = profile("refreshBlockCache/buildWindow")) {
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                        long chunkKey = ChunkPos.pack(chunkX, chunkZ);
                        activeChunkKeys.add(chunkKey);
                        ChunkScanResult cached = BLOCK_CHUNK_CACHE.get(chunkKey);
                        if (cached == null || refreshExpired || chunkWindowChanged) {
                            if (PENDING_BLOCK_SCAN_KEYS.add(chunkKey)) {
                                newlyQueuedChunks++;
                            }
                        }
                    }
                }
            }
            count("refreshBlockCache/activeChunkKeys", activeChunkKeys.size());
            count("refreshBlockCache/newlyQueuedChunks", newlyQueuedChunks);

            try (ModProfiler.Scope pruneScope = profile("refreshBlockCache/pruneCaches")) {
                BLOCK_CHUNK_CACHE.keySet().removeIf(chunkKey -> !activeChunkKeys.contains(chunkKey));
                DIRTY_CHUNK_MARK_TIMES.keySet().removeIf(chunkKey -> !activeChunkKeys.contains(chunkKey));
                DIRTY_BLOCK_POSITIONS.keySet().removeIf(chunkKey -> !activeChunkKeys.contains(chunkKey));
                PENDING_BLOCK_SCAN_KEYS.removeIf(chunkKey -> !activeChunkKeys.contains(chunkKey));
                BLOCK_SCAN_CURSORS.keySet().removeIf(chunkKey -> !activeChunkKeys.contains(chunkKey));
            }

            count("refreshBlockCache/cacheChunksAfterPrune", BLOCK_CHUNK_CACHE.size());
            count("refreshBlockCache/pendingChunksAfterPrune", PENDING_BLOCK_SCAN_KEYS.size());
            processDirtyChunks(mc, minChunkX, maxChunkX, minChunkZ, maxChunkZ);
            boolean published = processPendingChunkScans(mc);
            if (published || chunkWindowChanged) rebuildBlockBoxes();
        }
    }

    /** A scan retains its old published result until the complete new window is ready. */
    private static final class ChunkScanCursor {
        private final LevelChunk chunk;
        private final int minX, minY, minZ, maxY, xSpan, zSpan, layerSize, total;
        private final Map<Long, ColoredBox> boxes = new HashMap<>();
        private final Map<Long, HighlightBlockType> barrels = new HashMap<>();
        private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        private int index;

        private ChunkScanCursor(Minecraft mc, LevelChunk chunk) {
            this.chunk = chunk;
            BlockPos center = lastScanCenter != null ? lastScanCenter : mc.player.blockPosition();
            int worldMinY = mc.level.dimensionType().minY();
            minY = Math.max(worldMinY, center.getY() - VERTICAL_SCAN_RADIUS);
            maxY = Math.min(worldMinY + mc.level.dimensionType().height() - 1, center.getY() + VERTICAL_SCAN_RADIUS);
            minX = Math.max(chunk.getPos().getMinBlockX(), center.getX() - HORIZONTAL_SCAN_RADIUS);
            minZ = Math.max(chunk.getPos().getMinBlockZ(), center.getZ() - HORIZONTAL_SCAN_RADIUS);
            xSpan = Math.max(0, Math.min(chunk.getPos().getMaxBlockX(), center.getX() + HORIZONTAL_SCAN_RADIUS) - minX + 1);
            zSpan = Math.max(0, Math.min(chunk.getPos().getMaxBlockZ(), center.getZ() + HORIZONTAL_SCAN_RADIUS) - minZ + 1);
            layerSize = xSpan * zSpan;
            total = Math.max(0, maxY - minY + 1) * layerSize;
        }

        private int advance(Minecraft mc, int stepBudget, long deadline) {
            try (var ignored = profile("scanChunk"); var iterate = profile("scanChunk/iterateBlocks")) {
                int steps = 0;
                while (index < total && steps < stepBudget) {
                    if ((steps & 127) == 0 && System.nanoTime() >= deadline) break;
                    int y = minY + index / layerSize;
                    if (chunk.getSection(chunk.getSectionIndex(y)).hasOnlyAir()) {
                        int nextY = Math.min(maxY + 1, (Math.floorDiv(y, 16) + 1) * 16);
                        int next = (nextY - minY) * layerSize;
                        count("scanChunk/skippedAirBlocks", next - index);
                        index = next;
                        continue;
                    }
                    int x = minX + (index / zSpan) % xSpan;
                    int z = minZ + index % zSpan;
                    position.set(x, y, z);
                    index++; steps++;
                    BlockState state = chunk.getBlockState(position);
                    if (!HighlightBlockType.canMatch(state)) continue;
                    HighlightBlockType type = HighlightBlockType.from(state, chunk.getBlockEntity(position));
                    if (type == null) continue;
                    long key = position.asLong();
                    boxes.put(key, new ColoredBox(type.createBox(mc.level, position, state), type.red, type.green, type.blue));
                    if (type.isBarrel()) barrels.put(key, type);
                }
                count("scanChunk/visitedBlocks", steps);
                return steps;
            }
        }
    }

    private static void processDirtyChunks(Minecraft mc, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        try (ModProfiler.Scope ignored = profile("processDirtyChunks")) {
            if (DIRTY_CHUNK_KEYS.isEmpty()) {
                count("processDirtyChunks/skippedEmpty");
                return;
            }

            count("processDirtyChunks/dirtyQueueSize", DIRTY_CHUNK_KEYS.size());
            List<Long> rescannedChunkKeys = new ArrayList<>();
            int rescanned = 0;
            int skippedOutOfWindow = 0;
            int skippedMissingChunk = 0;
            int locallyPatched = 0;
            int escalatedFullScan = 0;
            try (ModProfiler.Scope iterateScope = profile("processDirtyChunks/iterate")) {
                for (long chunkKey : new ArrayList<>(DIRTY_CHUNK_KEYS)) {
                    int chunkX = ChunkPos.getX(chunkKey);
                    int chunkZ = ChunkPos.getZ(chunkKey);
                    if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
                        skippedOutOfWindow++;
                        rescannedChunkKeys.add(chunkKey);
                        continue;
                    }

                    LevelChunk chunk = mc.level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) {
                        skippedMissingChunk++;
                        rescannedChunkKeys.add(chunkKey);
                        continue;
                    }

                    Set<Long> dirtyPositions = DIRTY_BLOCK_POSITIONS.getOrDefault(chunkKey, Set.of());
                    if (shouldDoFullDirtyRescan(dirtyPositions)) {
                        PENDING_BLOCK_SCAN_KEYS.add(chunkKey);
                        BLOCK_SCAN_CURSORS.remove(chunkKey);
                        escalatedFullScan++;
                    } else {
                        updateChunkScanResult(chunkKey, rescanDirtyPositions(mc, chunk, dirtyPositions));
                        locallyPatched++;
                    }

                    rescannedChunkKeys.add(chunkKey);
                    rescanned++;
                    if (rescanned >= DIRTY_CHUNK_RESCAN_LIMIT) {
                        break;
                    }
                }
            }

            count("processDirtyChunks/rescanned", rescanned);
            count("processDirtyChunks/skippedOutOfWindow", skippedOutOfWindow);
            count("processDirtyChunks/skippedMissingChunk", skippedMissingChunk);
            count("processDirtyChunks/locallyPatched", locallyPatched);
            count("processDirtyChunks/escalatedFullScan", escalatedFullScan);
            count("processDirtyChunks/completedKeys", rescannedChunkKeys.size());
            if (rescannedChunkKeys.isEmpty()) {
                return;
            }

            DIRTY_CHUNK_KEYS.removeAll(rescannedChunkKeys);
            DIRTY_BLOCK_POSITIONS.keySet().removeIf(rescannedChunkKeys::contains);
            if (locallyPatched > 0) rebuildBlockBoxes();
        }
    }

    private static boolean processPendingChunkScans(Minecraft mc) {
        try (ModProfiler.Scope ignored = profile("processPendingChunkScans")) {
            if (PENDING_BLOCK_SCAN_KEYS.isEmpty()) return false;
            long deadline = System.nanoTime() + TARGET_CHUNK_SCAN_BUDGET_NANOS;
            int remainingSteps = MAX_BLOCK_SCAN_STEPS_PER_FRAME;
            int completed = 0;
            List<Long> nearestFirst = new ArrayList<>(PENDING_BLOCK_SCAN_KEYS);
            ChunkPos playerChunk = mc.player.chunkPosition();
            nearestFirst.sort(java.util.Comparator.comparingInt(key ->
                    Math.abs(ChunkPos.getX(key) - playerChunk.x()) + Math.abs(ChunkPos.getZ(key) - playerChunk.z())));
            for (long key : nearestFirst) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                if (chunk == null) {
                    BLOCK_CHUNK_CACHE.remove(key); BLOCK_SCAN_CURSORS.remove(key); PENDING_BLOCK_SCAN_KEYS.remove(key);
                    completed++;
                    continue;
                }
                ChunkScanCursor cursor = BLOCK_SCAN_CURSORS.get(key);
                if (cursor == null || cursor.chunk != chunk) {
                    cursor = new ChunkScanCursor(mc, chunk);
                    BLOCK_SCAN_CURSORS.put(key, cursor);
                }
                remainingSteps -= cursor.advance(mc, remainingSteps, deadline);
                if (cursor.index == cursor.total) {
                    updateChunkScanResult(key, new ChunkScanResult(cursor.boxes, cursor.barrels));
                    PENDING_BLOCK_SCAN_KEYS.remove(key); BLOCK_SCAN_CURSORS.remove(key);
                    count("scanChunk/outputBoxes", cursor.boxes.size());
                    completed++;
                }
                if (remainingSteps <= 0 || System.nanoTime() >= deadline) break;
            }
            count("processPendingChunkScans/visitedBlocks", MAX_BLOCK_SCAN_STEPS_PER_FRAME - remainingSteps);
            count("processPendingChunkScans/completed", completed);
            count("processPendingChunkScans/pendingQueueSize", PENDING_BLOCK_SCAN_KEYS.size());
            return completed != 0;
        }
    }

    private static void rebuildBlockBoxes() {
        try (ModProfiler.Scope ignored = profile("rebuildBlockBoxes")) {
            BLOCK_BOXES.clear();
            var barrels = new ArrayList<NearbyMineState.Point>();
            int cacheEntries = 0;
            for (ChunkScanResult result : BLOCK_CHUNK_CACHE.values()) {
                cacheEntries++;
                BLOCK_BOXES.addAll(result.boxesByBlockPos.values());
                for (long key : result.barrelsByBlockPos.keySet()) {
                    var pos=BlockPos.of(key); barrels.add(new NearbyMineState.Point(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5));
                }
            }
            NearbyMineState.barrels(barrels);
            count("rebuildBlockBoxes/cacheEntries", cacheEntries);
            count("rebuildBlockBoxes/outputBoxes", BLOCK_BOXES.size());
        }
    }

    private static void updateChunkScanResult(long chunkKey, ChunkScanResult next) {
        ChunkScanResult previous = BLOCK_CHUNK_CACHE.put(chunkKey, next);
        Map<Long, HighlightBlockType> previousBarrels = previous == null
                ? Map.of()
                : previous.barrelsByBlockPos;

        for (Map.Entry<Long, HighlightBlockType> entry : next.barrelsByBlockPos.entrySet()) {
            if (!ConfigManager.get().render.usefulItemsHighlight) break;
            if (previousBarrels.get(entry.getKey()) == entry.getValue()) {
                continue;
            }

            BlockPos position = BlockPos.of(entry.getKey());
            PopUpManager.getInstance().publish(PopUpRequest.of(
                    PopUpSource.BARREL_FOUND,
                    "Barrel found",
                    entry.getValue().barrelDisplayName()
                            + " at " + position.getX() + ", " + position.getY() + ", " + position.getZ(),
                    PopUpSeverity.SUCCESS
            ));
            count("barrelFound/published");
        }
    }

    private static void refreshEntityCacheIfNeeded(Minecraft mc) {
        try (ModProfiler.Scope ignored = profile("refreshEntityCache")) {
            long gameTime = mc.level.getGameTime();
            if (gameTime == lastEntityScanTick) {
                count("refreshEntityCache/skippedSameTick");
                return;
            }

            // A successful empty scan is still a cached result. Rechecking it every tick
            // made an empty mine more expensive than one with highlighted entities.
            if (lastEntityScanTick != Long.MIN_VALUE && gameTime >= lastEntityScanTick
                    && gameTime - lastEntityScanTick < ENTITY_SCAN_INTERVAL_TICKS && !entityScanDirty) {
                count("refreshEntityCache/skippedInterval");
                return;
            }

            lastEntityScanTick = gameTime;
            entityScanDirty = false;
            ENTITY_BOXES.clear();
            collectNearbyEntities(mc);
            count("refreshEntityCache/outputBoxes", ENTITY_BOXES.size());
        }
    }

    private static void collectNearbyEntities(Minecraft mc) {
        try (ModProfiler.Scope ignored = profile("collectNearbyEntities")) {
            AABB searchBox = mc.player.getBoundingBox().inflate(HORIZONTAL_SCAN_RADIUS, VERTICAL_SCAN_RADIUS, HORIZONTAL_SCAN_RADIUS);
            List<Entity> candidates = mc.level.getEntities(
                    mc.player,
                    searchBox,
                    candidate -> candidate != mc.player
                            && !candidate.isRemoved()
                            && (candidate instanceof ArmorStand || candidate instanceof Interaction)
            );
            count("collectNearbyEntities/candidates", candidates.size());
            CrystalParts parts = new CrystalParts(mc, candidates);
            int highlightedEntities = 0;
            Map<Long, BlockPos> goldenCrystalPositions = new LinkedHashMap<>();
            var worms = new ArrayList<NearbyMineState.Point>();
            try (ModProfiler.Scope classifyScope = profile("collectNearbyEntities/classify")) {
                for (Entity entity : candidates) {
                    if (!entity.getBoundingBox().intersects(searchBox)) continue;
                    AABB cluster = entity instanceof Interaction interaction ? parts.clusterBox(interaction) : null;
                    HighlightEntityType entityType = cluster != null ? HighlightEntityType.GOLDEN_CRYSTAL : HighlightEntityType.from(entity);
                    if (entityType == null) {
                        continue;
                    }

                    highlightedEntities++;
                    AABB box = cluster != null ? cluster : entityType.createBox(entity);
                    ENTITY_BOXES.add(new ColoredBox(box, entityType.red, entityType.green, entityType.blue));
                    if (entityType == HighlightEntityType.GOLDEN_CRYSTAL) {
                        BlockPos position = BlockPos.containing(box.getCenter());
                        goldenCrystalPositions.putIfAbsent(position.asLong(), position);
                    }
                    if (entityType == HighlightEntityType.WORM) worms.add(new NearbyMineState.Point(entity.getX(),entity.getY(),entity.getZ()));
                }
            }
            NearbyMineState.entities(worms, goldenCrystalPositions.values().stream()
                    .map(pos->new NearbyMineState.Point(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)).toList());
            if (ConfigManager.get().render.usefulItemsHighlight) publishNewGoldenCrystals(goldenCrystalPositions);
            count("collectNearbyEntities/highlighted", highlightedEntities);
            count("collectNearbyEntities/goldenCrystals", goldenCrystalPositions.size());
        }
    }

    private static void publishNewGoldenCrystals(Map<Long, BlockPos> positions) {
        Set<Long> discovered = GOLDEN_CRYSTAL_TRACKER.update(positions.keySet());
        for (long positionKey : discovered) {
            BlockPos position = positions.get(positionKey);
            if (position == null) {
                continue;
            }

            PopUpManager.getInstance().publish(PopUpRequest.of(
                    PopUpSource.GOLDEN_CRYSTAL_FOUND,
                    "Golden crystal found",
                    "At " + position.getX() + ", " + position.getY() + ", " + position.getZ(),
                    PopUpSeverity.SUCCESS
            ));
            count("goldenCrystalFound/published");
        }
    }

    private static void clearCache() {
        NearbyMineState.clear();
        CACHED_BOXES.clear();
        BLOCK_BOXES.clear();
        ENTITY_BOXES.clear();
        // Discovery memory belongs to the location, not these short-lived render caches.
        BLOCK_CHUNK_CACHE.clear();
        DIRTY_CHUNK_MARK_TIMES.clear();
        DIRTY_CHUNK_KEYS.clear();
        DIRTY_BLOCK_POSITIONS.clear();
        PENDING_BLOCK_SCAN_KEYS.clear(); BLOCK_SCAN_CURSORS.clear();
        lastEntityScanTick = Long.MIN_VALUE;
        lastBlockRefreshTick = Long.MIN_VALUE;
        lastMinChunkX = Integer.MIN_VALUE;
        lastMaxChunkX = Integer.MIN_VALUE;
        lastMinChunkZ = Integer.MIN_VALUE;
        lastMaxChunkZ = Integer.MIN_VALUE;
        lastScanCenter = null;
        entityScanDirty = true;
    }

    private static boolean isUsefulHighlightEntity(Entity entity) {
        return entity instanceof ArmorStand
                || entity instanceof Interaction
                || entity instanceof Display.ItemDisplay;
    }

    public static boolean isMineLocation() { return isMineHighlightLocation(); }
    private static boolean counterAdded(ru.wilyfox.client.hud.config.WidgetCatalog catalog) { return ConfigManager.isWidgetInCurrentLayout(catalog.key()) && isMineHighlightLocation(); }
    private static boolean needsBlockDetection() { return ConfigManager.get().render.usefulItemsHighlight || counterAdded(ru.wilyfox.client.hud.config.WidgetCatalog.NEARBY_BARRELS); }
    private static boolean needsDetection() { return needsBlockDetection() || counterAdded(ru.wilyfox.client.hud.config.WidgetCatalog.NEARBY_WORMS) || counterAdded(ru.wilyfox.client.hud.config.WidgetCatalog.NEARBY_CRYSTALS); }

    private record ColoredBox(AABB box, float red, float green, float blue) {
    }

    public record DiagnosticSnapshot(
            int cachedBoxes,
            int blockBoxes,
            int entityBoxes,
            int cachedChunks,
            int dirtyChunks,
            int dirtyBlockPositions,
            int pendingChunkScans
    ) {
    }

    private record ChunkScanResult(
            Map<Long, ColoredBox> boxesByBlockPos,
            Map<Long, HighlightBlockType> barrelsByBlockPos
    ) {
    }

    private enum HighlightEntityType {
        GOLDEN_CRYSTAL(1.0F, 0.82F, 0.15F, 0.12D),
        WORM(1.0F, 0.55F, 0.20F, 0.10D);

        private final float red;
        private final float green;
        private final float blue;
        private final double inflate;

        HighlightEntityType(float red, float green, float blue, double inflate) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.inflate = inflate;
        }

        private static HighlightEntityType from(Entity entity) {
            if (entity instanceof ArmorStand stand) {
                if (isLegacyGoldenCrystal(stand)) {
                    return GOLDEN_CRYSTAL;
                }

                var customName = stand.getCustomName();
                if (customName != null) {
                    String name = customName.getString();
                    if (!name.isBlank() && (name.contains("Червь") || name.contains("Р§РµСЂРІСЊ"))) return WORM;
                }
            }

            return null;
        }

        private static boolean isLegacyGoldenCrystal(ArmorStand stand) {
            for (var slot : ARMOR_SLOTS) {
                ItemStack stack = stand.getItemBySlot(slot);
                if (matchesCustomModel(stack, GOLDEN_CRYSTAL_MODEL_ID)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isEmptyArmorStand(ArmorStand stand) {
            for (var slot : ARMOR_SLOTS) {
                ItemStack stack = stand.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private AABB createBox(Entity entity) {
            if (this == GOLDEN_CRYSTAL) {
                return AABB.ofSize(
                        new Vec3(entity.getX(), entity.getY() + GOLDEN_CRYSTAL_BOX_Y_OFFSET, entity.getZ()),
                        GOLDEN_CRYSTAL_BOX_WIDTH,
                        GOLDEN_CRYSTAL_BOX_HEIGHT,
                        GOLDEN_CRYSTAL_BOX_WIDTH
                );
            }

            return entity.getBoundingBox().inflate(inflate);
        }

    }

    /** One short-lived spatial index per refresh; equipment/models are read once, never cached across packets. */
    private static final class CrystalParts {
        private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<List<CrystalPart>> cells =
                new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        private final List<CrystalPart> oversized = new ArrayList<>();
        private final List<CrystalPart> all = new ArrayList<>();
        private int query;

        private CrystalParts(Minecraft mc, List<Entity> candidates) {
            try (var ignored = profile("collectNearbyEntities/indexParts")) {
                AABB search = null;
                for (Entity candidate : candidates) {
                    if (candidate instanceof Interaction) {
                        AABB area = candidate.getBoundingBox().inflate(GOLDEN_CRYSTAL_CLUSTER_RADIUS);
                        search = search == null ? area : search.minmax(area);
                    }
                }
                if (search == null) return;
                List<Entity> entities = mc.level.getEntities(mc.player, search,
                        entity -> !entity.isRemoved() && (entity instanceof ArmorStand || entity instanceof Display.ItemDisplay));
                for (Entity entity : entities) {
                    int kind = entity instanceof ArmorStand stand && HighlightEntityType.isEmptyArmorStand(stand) ? 1
                            : entity instanceof Display.ItemDisplay display ? crystalPartKind(display.getItemStack()) : 0;
                    if (kind == 0) continue;
                    var part = new CrystalPart(entity.getBoundingBox(), kind);
                    all.add(part);
                    AABB box = part.box;
                    int x0 = cell(box.minX), x1 = cell(box.maxX), y0 = cell(box.minY), y1 = cell(box.maxY), z0 = cell(box.minZ), z1 = cell(box.maxZ);
                    if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 4096) { oversized.add(part); continue; }
                    for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
                        cells.computeIfAbsent(BlockPos.asLong(x, y, z), key -> new ArrayList<>()).add(part);
                    }
                }
            }
        }

        private AABB clusterBox(Interaction interaction) {
            AABB area = interaction.getBoundingBox().inflate(GOLDEN_CRYSTAL_CLUSTER_RADIUS);
            AABB box = interaction.getBoundingBox();
            int kinds = 0;
            int stamp = ++query;
            // Do not walk a huge grid for an unusually large custom interaction.
            if ((long) (cell(area.maxX) - cell(area.minX) + 1) * (cell(area.maxY) - cell(area.minY) + 1)
                    * (cell(area.maxZ) - cell(area.minZ) + 1) > 4096) {
                for (CrystalPart part : all) {
                    if (part.box.intersects(area)) { kinds |= part.kind; box = box.minmax(part.box); }
                }
                return kinds == 7 ? box.inflate(.08D, .12D, .08D) : null;
            }
            for (int x = cell(area.minX); x <= cell(area.maxX); x++)
                for (int y = cell(area.minY); y <= cell(area.maxY); y++)
                    for (int z = cell(area.minZ); z <= cell(area.maxZ); z++) {
                        List<CrystalPart> parts = cells.get(BlockPos.asLong(x, y, z));
                        if (parts == null) continue;
                        for (CrystalPart part : parts) {
                            if (part.visited == stamp) continue;
                            part.visited = stamp;
                            if (part.box.intersects(area)) { kinds |= part.kind; box = box.minmax(part.box); }
                        }
                    }
            for (CrystalPart part : oversized) {
                if (part.box.intersects(area)) { kinds |= part.kind; box = box.minmax(part.box); }
            }
            return kinds == 7 ? box.inflate(.08D, .12D, .08D) : null;
        }

        private static int cell(double coordinate) { return Mth.floor(coordinate / 4D); }
    }

    private static final class CrystalPart {
        private final AABB box;
        private final int kind;
        private int visited;
        private CrystalPart(AABB box, int kind) { this.box = box; this.kind = kind; }
    }

    enum HighlightBlockType {
        GOLDEN_SHARD(1.0F, 0.79F, 0.16F),
        DIAMOND_SHARD(0.20F, 0.85F, 1.0F),
        BASE_LUCKY_BLOCK(1.0F, 0.92F, 0.15F),
        RARE_LUCKY_BLOCK(0.15F, 0.75F, 1.0F),
        LEGENDARY_LUCKY_BLOCK(0.70F, 0.20F, 1.0F),
        NORMAL_BARREL(0.25F, 0.95F, 0.35F),
        NETHER_BARREL(1.0F, 0.22F, 0.22F),
        END_BARREL(0.74F, 0.28F, 1.0F);

        private static final String GOLDEN_SHARD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTRiZjg5M2ZjNmRlZmFkMjE4Zjc4MzZlZmVmYmU2MzZmMWMyY2MxYmI2NTBjODJmY2NkOTlmMmMxZWU2In19fQ==";
        private static final String DIAMOND_SHARD_TEXTURE = "ewogICJ0aW1lc3RhbXAiIDogMTU5ODUyMjU3OTk4MiwKICAicHJvZmlsZUlkIiA6ICJmMDk3N2NmZWZlZmY0ZGM1OGUyMGIzOTVlMjBiYWJkYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJkaWFtb25kZHVkZTMiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjE3NjdmYWEzNjZjODA1Nzc5NTJmNWUwMDc4MTU5ZDU5NzdmMzcyMDJmMzhkNDgxN2Q0YTkyNDVhZDQ4YTkwZCIKICAgIH0KICB9Cn0=";
        private static final String BASE_LUCKY_BLOCK_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjM4YzBkMmYxZWMyNjc1NGRjYTNjN2NkYWUzMWYxZjE2NDg4M2Q0NTNlNjg4NjQzZGEwNDc1NjhlN2ZhNWNjOSJ9fX0=";
        private static final String RARE_LUCKY_BLOCK_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmUwMDJkOTc3MjNiOGNjOTgwMmQzMGZlOGU0Y2VmMzYxZTU2Y2YyZTQ5YWU5MWYyNzRkYTcyZjQ3ODEzNDExOCJ9fX0=";
        private static final String LEGENDARY_LUCKY_BLOCK_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTA2ZWExMDRjYjliZTcwM2NjZWQxYjFmNTY1Mjg2NzUyZTI3MTc1MmM1YWM4NWU4MTEzYjNlMmRjNDM1MmMyMCJ9fX0=";
        private static final Map<String, HighlightBlockType> TEXTURE_TYPES = Map.of(
                SkinTextureId.fromValue(GOLDEN_SHARD_TEXTURE), GOLDEN_SHARD,
                SkinTextureId.fromValue(DIAMOND_SHARD_TEXTURE), DIAMOND_SHARD,
                SkinTextureId.fromValue(BASE_LUCKY_BLOCK_TEXTURE), BASE_LUCKY_BLOCK,
                SkinTextureId.fromValue(RARE_LUCKY_BLOCK_TEXTURE), RARE_LUCKY_BLOCK,
                SkinTextureId.fromValue(LEGENDARY_LUCKY_BLOCK_TEXTURE), LEGENDARY_LUCKY_BLOCK);

        private final float red;
        private final float green;
        private final float blue;

        HighlightBlockType(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        private boolean isBarrel() {
            return this == NORMAL_BARREL || this == NETHER_BARREL || this == END_BARREL;
        }

        private String barrelDisplayName() {
            return switch (this) {
                case NORMAL_BARREL -> "Normal barrel";
                case NETHER_BARREL -> "Nether barrel";
                case END_BARREL -> "End barrel";
                default -> "Barrel";
            };
        }

        static HighlightBlockType from(BlockState blockState, BlockEntity blockEntity) {
            // All block scans are already gated on the mine location by refreshBlockCacheIfNeeded.
            if (blockState.getBlock() instanceof PlayerHeadBlock || blockState.getBlock() instanceof PlayerWallHeadBlock) {
                String texture = readSkullTextureValue(blockEntity);
                if (texture == null) {
                    return null;
                }
                return fromTextureValue(texture);
            }

            if (!(blockState.getBlock() instanceof NoteBlock)) {
                return null;
            }

            NoteBlockInstrument instrument = blockState.getValue(NoteBlock.INSTRUMENT);
            int note = blockState.getValue(NoteBlock.NOTE);
            if (instrument != NoteBlockInstrument.FLUTE) {
                return null;
            }

            // Both powered states are the same barrel; powering only starts its detonation.
            return switch (note) {
                case 19 -> NORMAL_BARREL;
                case 18 -> NETHER_BARREL;
                case 17 -> END_BARREL;
                default -> null;
            };
        }

        private static boolean canMatch(BlockState blockState) {
            return blockState.getBlock() instanceof PlayerHeadBlock
                    || blockState.getBlock() instanceof PlayerWallHeadBlock
                    || blockState.getBlock() instanceof NoteBlock;
        }

        static HighlightBlockType fromTextureValue(String texture) {
            return TEXTURE_TYPES.get(SkinTextureId.fromValue(texture));
        }

        AABB createBox(BlockGetter level, BlockPos blockPos, BlockState blockState) {
            if (blockState.getBlock() instanceof PlayerHeadBlock || blockState.getBlock() instanceof PlayerWallHeadBlock) {
                // Skulls start at the floor; wall skulls also shift with their facing.
                // A fixed centered box floats above lucky blocks and misses wall variants.
                return blockState.getShape(level, blockPos).bounds().move(blockPos).inflate(0.02D);
            }

            return AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(blockPos)).inflate(0.02D);
        }

        private static String readSkullTextureValue(BlockEntity blockEntity) {
            if (!(blockEntity instanceof SkullBlockEntity skullBlockEntity)) {
                return null;
            }

            ResolvableProfile profile = skullBlockEntity.getOwnerProfile();
            if (profile == null) {
                return null;
            }

            Collection<Property> textures = profile.partialProfile().properties().get("textures");
            if (textures == null || textures.isEmpty()) {
                return null;
            }

            for (Property property : textures) {
                String value = property.value();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }

            return null;
        }
    }

    private static boolean isMineHighlightLocation() {
        String locationId = normalizeLocationId(DiamondWorldProtocolClient.getCurrentGameLocation());
        if (locationId == null) {
            return false;
        }

        if (DiamondWorldProtocolClient.isDungeonLocation() || DiamondWorldProtocolClient.isSiegeLocation()) {
            return false;
        }

        if (DiamondWorldProtocolClient.isCurrentFishingLocation()) {
            return false;
        }

        CurrentServerInfo serverInfo = DiamondWorldProtocolClient.getCurrentServerInfo();
        if ("HUB".equals(serverInfo.family())) {
            return false;
        }

        String sanitizedLocation = Formatting.sanitize(locationId).toLowerCase(java.util.Locale.ROOT);
        if (sanitizedLocation.contains("hub")
                || sanitizedLocation.contains("spawn")
                || sanitizedLocation.contains("lobby")
                || sanitizedLocation.contains("boss")
                || sanitizedLocation.contains("kriger")
                || sanitizedLocation.contains("guardian")
                || sanitizedLocation.contains("fish")) {
            return false;
        }

        return sanitizedLocation.contains("mine")
                || sanitizedLocation.contains("shaft")
                || sanitizedLocation.contains("quarry")
                || sanitizedLocation.contains("cave");
    }

    private static String normalizeLocationId(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static boolean matchesCustomModel(ItemStack stack, int modelId) {
        if (stack.isEmpty()) {
            return false;
        }

        CustomModelData customModelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (customModelData == null) {
            return false;
        }

        Float firstValue = customModelData.getFloat(0);
        return firstValue != null && Mth.floor(firstValue) == modelId;
    }

    private static int crystalPartKind(ItemStack stack) {
        var model = stack.get(DataComponents.ITEM_MODEL);
        if (model == null || !model.getNamespace().equals("modelengine")) return 0;
        if (model.getPath().startsWith("fragment_")) return 2;
        return model.getPath().startsWith("internal_fire/") ? 4 : 0;
    }

    private static ChunkScanResult rescanDirtyPositions(Minecraft mc, LevelChunk chunk, Set<Long> dirtyPositions) {
        try (ModProfiler.Scope ignored = profile("rescanDirtyPositions")) {
            ChunkScanResult current = BLOCK_CHUNK_CACHE.get(chunk.getPos().pack());
            Map<Long, ColoredBox> boxesByBlockPos = current == null
                    ? new HashMap<>()
                    : new HashMap<>(current.boxesByBlockPos);
            Map<Long, HighlightBlockType> barrelsByBlockPos = current == null
                    ? new HashMap<>()
                    : new HashMap<>(current.barrelsByBlockPos);
            if (dirtyPositions.isEmpty()) {
                count("rescanDirtyPositions/skippedEmpty");
                return new ChunkScanResult(boxesByBlockPos, barrelsByBlockPos);
            }

            BlockPos playerPos = mc.player.blockPosition();
            int worldMinY = mc.level.dimensionType().minY();
            int worldMaxY = worldMinY + mc.level.dimensionType().height() - 1;
            int minY = Math.max(worldMinY, playerPos.getY() - VERTICAL_SCAN_RADIUS);
            int maxY = Math.min(worldMaxY, playerPos.getY() + VERTICAL_SCAN_RADIUS);
            int minX = playerPos.getX() - HORIZONTAL_SCAN_RADIUS;
            int maxX = playerPos.getX() + HORIZONTAL_SCAN_RADIUS;
            int minZ = playerPos.getZ() - HORIZONTAL_SCAN_RADIUS;
            int maxZ = playerPos.getZ() + HORIZONTAL_SCAN_RADIUS;

            Set<Long> affectedPositions = expandDirtyPositions(chunk, dirtyPositions, minX, maxX, minY, maxY, minZ, maxZ);
            count("rescanDirtyPositions/dirtyPositions", dirtyPositions.size());
            count("rescanDirtyPositions/affectedPositions", affectedPositions.size());
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            int updatedBoxes = 0;
            int removedBoxes = 0;
            try (ModProfiler.Scope iterateScope = profile("rescanDirtyPositions/iterate")) {
                for (long blockPosLong : affectedPositions) {
                    cursor.set(BlockPos.of(blockPosLong));
                    BlockState blockState = chunk.getBlockState(cursor);
                    if (!HighlightBlockType.canMatch(blockState)) {
                        if (boxesByBlockPos.remove(blockPosLong) != null) {
                            removedBoxes++;
                        }
                        barrelsByBlockPos.remove(blockPosLong);
                        continue;
                    }

                    HighlightBlockType blockType = HighlightBlockType.from(blockState, chunk.getBlockEntity(cursor));
                    if (blockType == null) {
                        if (boxesByBlockPos.remove(blockPosLong) != null) {
                            removedBoxes++;
                        }
                        barrelsByBlockPos.remove(blockPosLong);
                        continue;
                    }

                    boxesByBlockPos.put(blockPosLong, new ColoredBox(blockType.createBox(mc.level, cursor, blockState), blockType.red, blockType.green, blockType.blue));
                    if (blockType.isBarrel()) {
                        barrelsByBlockPos.put(blockPosLong, blockType);
                    } else {
                        barrelsByBlockPos.remove(blockPosLong);
                    }
                    updatedBoxes++;
                }
            }
            count("rescanDirtyPositions/updatedBoxes", updatedBoxes);
            count("rescanDirtyPositions/removedBoxes", removedBoxes);
            count("rescanDirtyPositions/outputBoxes", boxesByBlockPos.size());
            return new ChunkScanResult(boxesByBlockPos, barrelsByBlockPos);
        }
    }

    private static Set<Long> expandDirtyPositions(LevelChunk chunk, Set<Long> dirtyPositions, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        Set<Long> affected = new HashSet<>();
        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMaxX = chunk.getPos().getMaxBlockX();
        int chunkMinZ = chunk.getPos().getMinBlockZ();
        int chunkMaxZ = chunk.getPos().getMaxBlockZ();
        for (long dirtyPosLong : dirtyPositions) {
            BlockPos dirtyPos = BlockPos.of(dirtyPosLong);
            for (int dx = -DIRTY_LOCAL_RESCAN_RADIUS; dx <= DIRTY_LOCAL_RESCAN_RADIUS; dx++) {
                for (int dy = -DIRTY_LOCAL_RESCAN_RADIUS; dy <= DIRTY_LOCAL_RESCAN_RADIUS; dy++) {
                    for (int dz = -DIRTY_LOCAL_RESCAN_RADIUS; dz <= DIRTY_LOCAL_RESCAN_RADIUS; dz++) {
                        int x = dirtyPos.getX() + dx;
                        int y = dirtyPos.getY() + dy;
                        int z = dirtyPos.getZ() + dz;
                        if (x < chunkMinX || x > chunkMaxX || z < chunkMinZ || z > chunkMaxZ) {
                            continue;
                        }
                        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                            continue;
                        }
                        affected.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }
        return affected;
    }

    private static boolean shouldDoFullDirtyRescan(Set<Long> dirtyPositions) {
        return dirtyPositions.isEmpty() || dirtyPositions.size() >= DIRTY_FULL_RESCAN_THRESHOLD;
    }

    private static ModProfiler.Scope profile(String section) {
        ModProfiler profiler = ModProfiler.getInstance();
        return profiler.scope(profiler.isEnabled() ? PROFILER_PREFIX + "/" + section : null);
    }

    private static void count(String counter) {
        ModProfiler profiler = ModProfiler.getInstance();
        if (profiler.isEnabled()) profiler.incrementCounter(PROFILER_PREFIX + "/" + counter);
    }

    private static void count(String counter, long delta) {
        ModProfiler profiler = ModProfiler.getInstance();
        if (profiler.isEnabled()) profiler.incrementCounter(PROFILER_PREFIX + "/" + counter, delta);
    }
}
