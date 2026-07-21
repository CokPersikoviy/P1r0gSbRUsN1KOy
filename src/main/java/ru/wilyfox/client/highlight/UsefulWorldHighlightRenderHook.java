package ru.wilyfox.client.highlight;

import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.ChunkPos;
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
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.protocol.CurrentServerInfo;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UsefulWorldHighlightRenderHook {
    private static final String PROFILER_PREFIX = "render/UsefulWorldHighlightRenderHook";
    private static final int GOLDEN_CRYSTAL_MODEL_ID = 271;
    private static final int ENTITY_SCAN_INTERVAL_TICKS = 10;
    private static final int BLOCK_SCAN_REFRESH_TICKS = 100;
    private static final int DIRTY_CHUNK_RESCAN_LIMIT = 2;
    private static final int DIRTY_LOCAL_RESCAN_RADIUS = 1;
    private static final int DIRTY_FULL_RESCAN_THRESHOLD = 6;
    private static final int MIN_CHUNK_SCAN_BUDGET_PER_FRAME = 1;
    private static final int MAX_CHUNK_SCAN_BUDGET_PER_FRAME = 3;
    private static final long TARGET_CHUNK_SCAN_BUDGET_NANOS = 4_000_000L;
    private static final long DIRTY_CHUNK_MARK_COOLDOWN_MS = 500L;
    private static final int HORIZONTAL_SCAN_RADIUS = 32;
    private static final int VERTICAL_SCAN_RADIUS = 20;
    private static final float LINE_ALPHA = 1.0F;
    private static final double GOLDEN_CRYSTAL_CLUSTER_RADIUS = 1.6D;
    private static final String GOLDEN_CRYSTAL_MODEL_PREFIX = "modelengine:fragment_";
    private static final String GOLDEN_CRYSTAL_FIRE_PREFIX = "modelengine:internal_fire/";
    private static final double GOLDEN_CRYSTAL_BOX_WIDTH = 0.58D;
    private static final double GOLDEN_CRYSTAL_BOX_HEIGHT = 0.46D;
    private static final double GOLDEN_CRYSTAL_BOX_Y_OFFSET = 0.28D;
    private static final RenderType USEFUL_HIGHLIGHT_NO_DEPTH = RenderType.create(
            "froghelper_useful_highlight_no_depth",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING_FORWARD)
                    .setLineState(RenderStateShard.DEFAULT_LINE)
                    .createCompositeState(false)
    );

    private static final List<ColoredBox> CACHED_BOXES = new ArrayList<>();
    private static final List<ColoredBox> BLOCK_BOXES = new ArrayList<>();
    private static final List<ColoredBox> ENTITY_BOXES = new ArrayList<>();
    private static final Map<Long, ChunkScanResult> BLOCK_CHUNK_CACHE = new HashMap<>();
    private static final Map<Long, Long> DIRTY_CHUNK_MARK_TIMES = new HashMap<>();
    private static final Set<Long> DIRTY_CHUNK_KEYS = new HashSet<>();
    private static final Map<Long, Set<Long>> DIRTY_BLOCK_POSITIONS = new HashMap<>();
    private static final LinkedHashSet<Long> PENDING_BLOCK_SCAN_KEYS = new LinkedHashSet<>();
    private static long lastEntityScanTick = Long.MIN_VALUE;
    private static long lastBlockRefreshTick = Long.MIN_VALUE;
    private static long averageChunkScanNanos = 10_000_000L;
    private static int lastMinChunkX = Integer.MIN_VALUE;
    private static int lastMaxChunkX = Integer.MIN_VALUE;
    private static int lastMinChunkZ = Integer.MIN_VALUE;
    private static int lastMaxChunkZ = Integer.MIN_VALUE;

    private UsefulWorldHighlightRenderHook() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(UsefulWorldHighlightRenderHook::onAfterEntities);
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

            if (!ConfigManager.get().render.usefulItemsHighlight || !isMineHighlightLocation()) {
                count("dirtyMark/skippedDisabled");
                return;
            }

            long chunkKey = ChunkPos.asLong(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
            DIRTY_CHUNK_KEYS.add(chunkKey);
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

    private static void onAfterEntities(WorldRenderContext context) {
        try (ModProfiler.Scope ignored = profile("frame")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || context.matrixStack() == null || context.consumers() == null) {
                count("frame/skippedNoContext");
                clearCache();
                return;
            }

            if (!ConfigManager.get().render.usefulItemsHighlight) {
                count("frame/skippedDisabled");
                clearCache();
                return;
            }

            refreshCacheIfNeeded(mc);
            if (CACHED_BOXES.isEmpty()) {
                count("frame/skippedEmptyBoxes");
                return;
            }

            Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
            PoseStack poseStack = context.matrixStack();
            VertexConsumer lineConsumer = context.consumers().getBuffer(USEFUL_HIGHLIGHT_NO_DEPTH);

            count("frame/cachedBoxes", CACHED_BOXES.size());
            try (ModProfiler.Scope drawScope = profile("drawBoxes")) {
                for (ColoredBox coloredBox : CACHED_BOXES) {
                    ShapeRenderer.renderLineBox(
                            poseStack,
                            lineConsumer,
                            coloredBox.box.move(-cameraPos.x, -cameraPos.y, -cameraPos.z),
                            coloredBox.red,
                            coloredBox.green,
                            coloredBox.blue,
                            LINE_ALPHA
                    );
                }
            }
        }
    }

    private static void refreshCacheIfNeeded(Minecraft mc) {
        try (ModProfiler.Scope ignored = profile("refreshCache")) {
            refreshBlockCacheIfNeeded(mc);
            refreshEntityCacheIfNeeded(mc);

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

    private static void refreshBlockCacheIfNeeded(Minecraft mc) {
        try (ModProfiler.Scope ignored = profile("refreshBlockCache")) {
            if (!isMineHighlightLocation()) {
                count("refreshBlockCache/skippedNotMine");
                BLOCK_BOXES.clear();
                BLOCK_CHUNK_CACHE.clear();
                DIRTY_CHUNK_MARK_TIMES.clear();
                DIRTY_CHUNK_KEYS.clear();
                DIRTY_BLOCK_POSITIONS.clear();
                PENDING_BLOCK_SCAN_KEYS.clear();
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
                    || maxChunkZ != lastMaxChunkZ;
            boolean refreshExpired = gameTime - lastBlockRefreshTick >= BLOCK_SCAN_REFRESH_TICKS;
            // PENDING_BLOCK_SCAN_KEYS empty means the current window has been fully scanned at
            // least once - unlike BLOCK_BOXES.isEmpty(), this stays true even when the scan
            // legitimately found nothing nearby, so the fast path doesn't get skipped every frame.
            if (!chunkWindowChanged && !refreshExpired && PENDING_BLOCK_SCAN_KEYS.isEmpty()) {
                count("refreshBlockCache/fastPathHits");
                processDirtyChunks(mc, minChunkX, maxChunkX, minChunkZ, maxChunkZ);
                return;
            }

            count(chunkWindowChanged ? "refreshBlockCache/windowChanged" : "refreshBlockCache/windowStable");
            count(refreshExpired ? "refreshBlockCache/refreshExpired" : "refreshBlockCache/refreshFresh");

            lastBlockRefreshTick = gameTime;
            lastMinChunkX = minChunkX;
            lastMaxChunkX = maxChunkX;
            lastMinChunkZ = minChunkZ;
            lastMaxChunkZ = maxChunkZ;

            Set<Long> activeChunkKeys = new HashSet<>();
            int newlyQueuedChunks = 0;
            try (ModProfiler.Scope buildWindowScope = profile("refreshBlockCache/buildWindow")) {
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
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
            }

            count("refreshBlockCache/cacheChunksAfterPrune", BLOCK_CHUNK_CACHE.size());
            count("refreshBlockCache/pendingChunksAfterPrune", PENDING_BLOCK_SCAN_KEYS.size());
            processPendingChunkScans(mc);
            rebuildBlockBoxes();
            processDirtyChunks(mc, minChunkX, maxChunkX, minChunkZ, maxChunkZ);
        }
    }

    private static ChunkScanResult scanChunk(Minecraft mc, LevelChunk chunk) {
        try (ModProfiler.Scope ignored = profile("scanChunk")) {
            long scanStartedAt = System.nanoTime();
            Map<Long, ColoredBox> boxesByBlockPos = new HashMap<>();
            BlockPos playerPos = mc.player.blockPosition();
            int worldMinY = mc.level.dimensionType().minY();
            int worldMaxY = worldMinY + mc.level.dimensionType().height() - 1;
            int minY = Math.max(worldMinY, playerPos.getY() - VERTICAL_SCAN_RADIUS);
            int maxY = Math.min(worldMaxY, playerPos.getY() + VERTICAL_SCAN_RADIUS);
            ChunkPos chunkPos = chunk.getPos();
            int minX = Math.max(chunkPos.getMinBlockX(), playerPos.getX() - HORIZONTAL_SCAN_RADIUS);
            int maxX = Math.min(chunkPos.getMaxBlockX(), playerPos.getX() + HORIZONTAL_SCAN_RADIUS);
            int minZ = Math.max(chunkPos.getMinBlockZ(), playerPos.getZ() - HORIZONTAL_SCAN_RADIUS);
            int maxZ = Math.min(chunkPos.getMaxBlockZ(), playerPos.getZ() + HORIZONTAL_SCAN_RADIUS);
            int visitedBlocks = 0;
            int highlightedBlocks = 0;
            count("scanChunk/ySpan", maxY - minY + 1);
            count("scanChunk/xSpan", maxX - minX + 1);
            count("scanChunk/zSpan", maxZ - minZ + 1);

            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            try (ModProfiler.Scope iterateScope = profile("scanChunk/iterateBlocks")) {
                for (int y = minY; y <= maxY; y++) {
                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            visitedBlocks++;
                            cursor.set(x, y, z);
                            BlockState blockState = chunk.getBlockState(cursor);
                            if (!HighlightBlockType.canMatch(blockState)) {
                                continue;
                            }

                            HighlightBlockType blockType = HighlightBlockType.from(blockState, chunk.getBlockEntity(cursor));
                            if (blockType == null) {
                                continue;
                            }

                            highlightedBlocks++;
                            boxesByBlockPos.put(cursor.asLong(), new ColoredBox(blockType.createBox(cursor, blockState), blockType.red, blockType.green, blockType.blue));
                        }
                    }
                }
            }
            count("scanChunk/visitedBlocks", visitedBlocks);
            count("scanChunk/highlightedBlocks", highlightedBlocks);
            count("scanChunk/outputBoxes", boxesByBlockPos.size());
            recordChunkScanSample(System.nanoTime() - scanStartedAt);
            return new ChunkScanResult(boxesByBlockPos);
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
                        BLOCK_CHUNK_CACHE.put(chunkKey, scanChunk(mc, chunk));
                        escalatedFullScan++;
                    } else {
                        BLOCK_CHUNK_CACHE.put(chunkKey, rescanDirtyPositions(mc, chunk, dirtyPositions));
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
            rebuildBlockBoxes();
        }
    }

    private static void processPendingChunkScans(Minecraft mc) {
        try (ModProfiler.Scope ignored = profile("processPendingChunkScans")) {
            if (PENDING_BLOCK_SCAN_KEYS.isEmpty()) {
                count("processPendingChunkScans/skippedEmpty");
                return;
            }

            count("processPendingChunkScans/pendingQueueSize", PENDING_BLOCK_SCAN_KEYS.size());
            int scanned = 0;
            int missingChunks = 0;
            int scanBudget = chunkScanBudgetPerFrame();
            List<Long> completedChunkKeys = new ArrayList<>();
            try (ModProfiler.Scope iterateScope = profile("processPendingChunkScans/iterate")) {
                for (long chunkKey : PENDING_BLOCK_SCAN_KEYS) {
                    int chunkX = ChunkPos.getX(chunkKey);
                    int chunkZ = ChunkPos.getZ(chunkKey);
                    LevelChunk chunk = mc.level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) {
                        missingChunks++;
                        BLOCK_CHUNK_CACHE.remove(chunkKey);
                        completedChunkKeys.add(chunkKey);
                        continue;
                    }

                    BLOCK_CHUNK_CACHE.put(chunkKey, scanChunk(mc, chunk));
                    completedChunkKeys.add(chunkKey);
                    scanned++;
                    if (scanned >= scanBudget) {
                        break;
                    }
                }
            }

            count("processPendingChunkScans/scanBudget", scanBudget);
            count("processPendingChunkScans/scanned", scanned);
            count("processPendingChunkScans/missingChunks", missingChunks);
            count("processPendingChunkScans/completed", completedChunkKeys.size());
            if (!completedChunkKeys.isEmpty()) {
                PENDING_BLOCK_SCAN_KEYS.removeAll(completedChunkKeys);
            }
        }
    }

    private static void rebuildBlockBoxes() {
        try (ModProfiler.Scope ignored = profile("rebuildBlockBoxes")) {
            BLOCK_BOXES.clear();
            int cacheEntries = 0;
            for (ChunkScanResult result : BLOCK_CHUNK_CACHE.values()) {
                cacheEntries++;
                BLOCK_BOXES.addAll(result.boxesByBlockPos.values());
            }
            count("rebuildBlockBoxes/cacheEntries", cacheEntries);
            count("rebuildBlockBoxes/outputBoxes", BLOCK_BOXES.size());
        }
    }

    private static void refreshEntityCacheIfNeeded(Minecraft mc) {
        try (ModProfiler.Scope ignored = profile("refreshEntityCache")) {
            long gameTime = mc.level.getGameTime();
            if (gameTime == lastEntityScanTick) {
                count("refreshEntityCache/skippedSameTick");
                return;
            }

            if (gameTime - lastEntityScanTick < ENTITY_SCAN_INTERVAL_TICKS && !ENTITY_BOXES.isEmpty()) {
                count("refreshEntityCache/skippedInterval");
                return;
            }

            lastEntityScanTick = gameTime;
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
                            && isUsefulHighlightEntity(candidate)
            );
            count("collectNearbyEntities/candidates", candidates.size());
            int highlightedEntities = 0;
            try (ModProfiler.Scope classifyScope = profile("collectNearbyEntities/classify")) {
                for (Entity entity : candidates) {
                    HighlightEntityType entityType = HighlightEntityType.from(entity);
                    if (entityType == null) {
                        continue;
                    }

                    highlightedEntities++;
                    ENTITY_BOXES.add(new ColoredBox(entityType.createBox(entity), entityType.red, entityType.green, entityType.blue));
                }
            }
            count("collectNearbyEntities/highlighted", highlightedEntities);
        }
    }

    private static void clearCache() {
        CACHED_BOXES.clear();
        BLOCK_BOXES.clear();
        ENTITY_BOXES.clear();
        BLOCK_CHUNK_CACHE.clear();
        DIRTY_CHUNK_MARK_TIMES.clear();
        DIRTY_CHUNK_KEYS.clear();
        DIRTY_BLOCK_POSITIONS.clear();
        PENDING_BLOCK_SCAN_KEYS.clear();
        lastEntityScanTick = Long.MIN_VALUE;
        lastBlockRefreshTick = Long.MIN_VALUE;
        averageChunkScanNanos = 10_000_000L;
        lastMinChunkX = Integer.MIN_VALUE;
        lastMaxChunkX = Integer.MIN_VALUE;
        lastMinChunkZ = Integer.MIN_VALUE;
        lastMaxChunkZ = Integer.MIN_VALUE;
    }

    private static boolean isUsefulHighlightEntity(Entity entity) {
        return entity instanceof ArmorStand
                || entity instanceof Interaction
                || entity instanceof Display.ItemDisplay;
    }

    private record ColoredBox(AABB box, float red, float green, float blue) {
    }

    private record ChunkScanResult(Map<Long, ColoredBox> boxesByBlockPos) {
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

                String name = entity.getName().getString();
                if (!name.isBlank() && (name.contains("Червь") || name.contains("Р§РµСЂРІСЊ"))) {
                    return WORM;
                }
            }

            if (entity instanceof Interaction interaction && isClusterGoldenCrystal(interaction)) {
                return GOLDEN_CRYSTAL;
            }

            return null;
        }

        private static boolean isLegacyGoldenCrystal(ArmorStand stand) {
            for (ItemStack stack : stand.getArmorSlots()) {
                if (matchesCustomModel(stack, GOLDEN_CRYSTAL_MODEL_ID)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isClusterGoldenCrystal(Interaction interaction) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return false;
            }

            List<Entity> nearbyEntities = mc.level.getEntities(
                    interaction,
                    interaction.getBoundingBox().inflate(GOLDEN_CRYSTAL_CLUSTER_RADIUS),
                    candidate -> candidate != interaction
            );

            boolean hasEmptyArmorStand = false;
            int goldenCrystalPartCount = 0;
            int firePartCount = 0;

            for (Entity nearbyEntity : nearbyEntities) {
                if (nearbyEntity instanceof ArmorStand stand && isEmptyArmorStand(stand)) {
                    hasEmptyArmorStand = true;
                    continue;
                }

                if (nearbyEntity instanceof Display.ItemDisplay itemDisplay) {
                    String itemModel = readItemModel(itemDisplay.getItemStack());
                    if (itemModel.startsWith(GOLDEN_CRYSTAL_MODEL_PREFIX)) {
                        goldenCrystalPartCount++;
                    } else if (itemModel.startsWith(GOLDEN_CRYSTAL_FIRE_PREFIX)) {
                        firePartCount++;
                    }
                }
            }

            return hasEmptyArmorStand && goldenCrystalPartCount >= 1 && firePartCount >= 1;
        }

        private static boolean isEmptyArmorStand(ArmorStand stand) {
            for (ItemStack stack : stand.getArmorSlots()) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private AABB createBox(Entity entity) {
            if (this == GOLDEN_CRYSTAL) {
                if (entity instanceof Interaction interaction) {
                    AABB clusterBox = buildGoldenCrystalClusterBox(interaction);
                    if (clusterBox != null) {
                        return clusterBox;
                    }
                }

                return AABB.ofSize(
                        new Vec3(entity.getX(), entity.getY() + GOLDEN_CRYSTAL_BOX_Y_OFFSET, entity.getZ()),
                        GOLDEN_CRYSTAL_BOX_WIDTH,
                        GOLDEN_CRYSTAL_BOX_HEIGHT,
                        GOLDEN_CRYSTAL_BOX_WIDTH
                );
            }

            return entity.getBoundingBox().inflate(inflate);
        }

        private AABB buildGoldenCrystalClusterBox(Interaction interaction) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return null;
            }

            AABB clusterBox = interaction.getBoundingBox();
            boolean foundPart = false;
            for (Entity nearbyEntity : mc.level.getEntities(
                    interaction,
                    interaction.getBoundingBox().inflate(GOLDEN_CRYSTAL_CLUSTER_RADIUS),
                    candidate -> candidate != interaction
            )) {
                if (nearbyEntity instanceof ArmorStand stand && isEmptyArmorStand(stand)) {
                    clusterBox = clusterBox.minmax(stand.getBoundingBox());
                    foundPart = true;
                    continue;
                }

                if (nearbyEntity instanceof Display.ItemDisplay itemDisplay) {
                    String itemModel = readItemModel(itemDisplay.getItemStack());
                    if (itemModel.startsWith(GOLDEN_CRYSTAL_MODEL_PREFIX) || itemModel.startsWith(GOLDEN_CRYSTAL_FIRE_PREFIX)) {
                        clusterBox = clusterBox.minmax(itemDisplay.getBoundingBox());
                        foundPart = true;
                    }
                }
            }

            return foundPart ? clusterBox.inflate(0.08D, 0.12D, 0.08D) : null;
        }
    }

    private enum HighlightBlockType {
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

        private final float red;
        private final float green;
        private final float blue;

        HighlightBlockType(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        private static HighlightBlockType from(BlockState blockState, BlockEntity blockEntity) {
            if (!isMineHighlightLocation()) {
                return null;
            }

            if (blockState.getBlock() instanceof PlayerHeadBlock || blockState.getBlock() instanceof PlayerWallHeadBlock) {
                String texture = readSkullTextureValue(blockEntity);
                if (texture == null) {
                    return null;
                }
                if (GOLDEN_SHARD_TEXTURE.equals(texture)) {
                    return GOLDEN_SHARD;
                }
                if (DIAMOND_SHARD_TEXTURE.equals(texture)) {
                    return DIAMOND_SHARD;
                }
                if (BASE_LUCKY_BLOCK_TEXTURE.equals(texture)) {
                    return BASE_LUCKY_BLOCK;
                }
                if (RARE_LUCKY_BLOCK_TEXTURE.equals(texture)) {
                    return RARE_LUCKY_BLOCK;
                }
                if (LEGENDARY_LUCKY_BLOCK_TEXTURE.equals(texture)) {
                    return LEGENDARY_LUCKY_BLOCK;
                }
                return null;
            }

            if (!(blockState.getBlock() instanceof NoteBlock)) {
                return null;
            }

            NoteBlockInstrument instrument = blockState.getValue(NoteBlock.INSTRUMENT);
            int note = blockState.getValue(NoteBlock.NOTE);
            if (instrument != NoteBlockInstrument.FLUTE) {
                return null;
            }

            return switch (note) {
                case 21, 22 -> NORMAL_BARREL;
                case 18, 19 -> NETHER_BARREL;
                case 16, 17 -> END_BARREL;
                default -> null;
            };
        }

        private static boolean canMatch(BlockState blockState) {
            return blockState.getBlock() instanceof PlayerHeadBlock
                    || blockState.getBlock() instanceof PlayerWallHeadBlock
                    || blockState.getBlock() instanceof NoteBlock;
        }

        private AABB createBox(BlockPos blockPos, BlockState blockState) {
            if (blockState.getBlock() instanceof PlayerHeadBlock || blockState.getBlock() instanceof PlayerWallHeadBlock) {
                return new AABB(
                        blockPos.getX() + 0.22D,
                        blockPos.getY() + 0.18D,
                        blockPos.getZ() + 0.22D,
                        blockPos.getX() + 0.78D,
                        blockPos.getY() + 0.82D,
                        blockPos.getZ() + 0.78D
                );
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

            Collection<Property> textures = profile.properties().get("textures");
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

        if (DiamondWorldProtocolClient.getFishingLocationIds().contains(locationId)) {
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

    private static String readItemModel(ItemStack stack) {
        Object itemModel = stack.get(DataComponents.ITEM_MODEL);
        return itemModel == null ? "" : itemModel.toString();
    }

    private static ChunkScanResult rescanDirtyPositions(Minecraft mc, LevelChunk chunk, Set<Long> dirtyPositions) {
        try (ModProfiler.Scope ignored = profile("rescanDirtyPositions")) {
            ChunkScanResult current = BLOCK_CHUNK_CACHE.get(chunk.getPos().toLong());
            Map<Long, ColoredBox> boxesByBlockPos = current == null
                    ? new HashMap<>()
                    : new HashMap<>(current.boxesByBlockPos);
            if (dirtyPositions.isEmpty()) {
                count("rescanDirtyPositions/skippedEmpty");
                return new ChunkScanResult(boxesByBlockPos);
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
                        continue;
                    }

                    HighlightBlockType blockType = HighlightBlockType.from(blockState, chunk.getBlockEntity(cursor));
                    if (blockType == null) {
                        if (boxesByBlockPos.remove(blockPosLong) != null) {
                            removedBoxes++;
                        }
                        continue;
                    }

                    boxesByBlockPos.put(blockPosLong, new ColoredBox(blockType.createBox(cursor, blockState), blockType.red, blockType.green, blockType.blue));
                    updatedBoxes++;
                }
            }
            count("rescanDirtyPositions/updatedBoxes", updatedBoxes);
            count("rescanDirtyPositions/removedBoxes", removedBoxes);
            count("rescanDirtyPositions/outputBoxes", boxesByBlockPos.size());
            return new ChunkScanResult(boxesByBlockPos);
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

    private static int chunkScanBudgetPerFrame() {
        long safeAverageNanos = Math.max(1_000_000L, averageChunkScanNanos);
        int dynamicBudget = (int) Math.max(MIN_CHUNK_SCAN_BUDGET_PER_FRAME, TARGET_CHUNK_SCAN_BUDGET_NANOS / safeAverageNanos);
        return Mth.clamp(dynamicBudget, MIN_CHUNK_SCAN_BUDGET_PER_FRAME, MAX_CHUNK_SCAN_BUDGET_PER_FRAME);
    }

    private static void recordChunkScanSample(long elapsedNanos) {
        long sample = Math.max(0L, elapsedNanos);
        if (sample <= 0L) {
            return;
        }

        averageChunkScanNanos = averageChunkScanNanos <= 0L
                ? sample
                : ((averageChunkScanNanos * 7L) + sample) / 8L;
        count("scanChunk/averageNanos", averageChunkScanNanos);
    }

    private static ModProfiler.Scope profile(String section) {
        return ModProfiler.getInstance().scope(PROFILER_PREFIX + "/" + section);
    }

    private static void count(String counter) {
        ModProfiler.getInstance().incrementCounter(PROFILER_PREFIX + "/" + counter);
    }

    private static void count(String counter, long delta) {
        ModProfiler.getInstance().incrementCounter(PROFILER_PREFIX + "/" + counter, delta);
    }
}
