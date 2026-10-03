package ru.wilyfox.client.alchemy;

import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import ru.wilyfox.bridge.BossHealthOverlayAccessor;
import ru.wilyfox.client.hud.config.ConfigManager;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AlchemyIngredientTracker {
    private static final AlchemyIngredientTracker INSTANCE = new AlchemyIngredientTracker();
    private static final long LIFETIME_MS = 2_000L;
    private static final long CLEANUP_INTERVAL_MS = 50L;

    private final Map<Long, AlchemyIngredientSpot> spots = new LinkedHashMap<>();
    private ClientLevel trackedLevel;
    private boolean registered;
    private long bossBarCheckedTick = Long.MIN_VALUE;
    private boolean alchemyBossBar;
    private long lastCleanupMillis;
    private boolean snapshotDirty;
    private List<AlchemyIngredientSpot> snapshot = List.of();

    private AlchemyIngredientTracker() {
    }

    public static AlchemyIngredientTracker getInstance() {
        return INSTANCE;
    }

    public void register() {
        if (registered) return;
        registered = true;
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clear();
            trackedLevel = null;
            bossBarCheckedTick = Long.MIN_VALUE;
            alchemyBossBar = false;
        });
    }

    public void addParticle(double x, double y, double z) {
        syncLevel();
        if (trackedLevel == null || !ConfigManager.get().render.showAlchemyIngredientMarkers || !hasAlchemyBossBarThisTick()) {
            clear();
            return;
        }

        long now = System.currentTimeMillis();
        Vec3 position = new Vec3(x, y, z);
        long blockKey = BlockPos.containing(x, y, z).asLong();
        spots.put(blockKey, new AlchemyIngredientSpot(position, now));
        snapshotDirty = true;
        cleanup(now);
    }

    public List<AlchemyIngredientSpot> getActiveSpots() {
        syncLevel();
        if (trackedLevel == null || !ConfigManager.get().render.showAlchemyIngredientMarkers || !hasAlchemyBossBarThisTick()) {
            clear();
            return List.of();
        }

        cleanup(System.currentTimeMillis());
        if (snapshotDirty) {
            snapshot = List.copyOf(spots.values());
            snapshotDirty = false;
        }
        return snapshot;
    }

    public void clear() {
        spots.clear();
        snapshot = List.of();
        snapshotDirty = false;
        lastCleanupMillis = 0L;
    }

    private void syncLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (trackedLevel != current) {
            clear();
            trackedLevel = current;
            bossBarCheckedTick = Long.MIN_VALUE;
            alchemyBossBar = false;
        }
    }

    private boolean hasAlchemyBossBarThisTick() {
        long tick = trackedLevel.getGameTime();
        if (bossBarCheckedTick != tick) {
            alchemyBossBar = hasAlchemyBossBar();
            bossBarCheckedTick = tick;
        }
        return alchemyBossBar;
    }

    public int diagnosticSpotCount() {
        syncLevel();
        cleanup(System.currentTimeMillis());
        return spots.size();
    }

    private void cleanup(long now) {
        if (now >= lastCleanupMillis && now - lastCleanupMillis < CLEANUP_INTERVAL_MS) return;
        lastCleanupMillis = now;
        Iterator<AlchemyIngredientSpot> iterator = spots.values().iterator();
        while (iterator.hasNext()) {
            AlchemyIngredientSpot spot = iterator.next();
            if (now - spot.createdAtMillis() > LIFETIME_MS) {
                iterator.remove();
                snapshotDirty = true;
            }
        }
    }

    private static boolean hasAlchemyBossBar() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null || !(minecraft.gui.hud.getBossOverlay() instanceof BossHealthOverlayAccessor accessor)) {
            return false;
        }

        for (LerpingBossEvent event : accessor.froghelper$getEvents()) {
            if (event.getColor() == BossEvent.BossBarColor.BLUE
                    && event.getOverlay() == BossEvent.BossBarOverlay.PROGRESS) {
                return true;
            }
        }
        return false;
    }
}
