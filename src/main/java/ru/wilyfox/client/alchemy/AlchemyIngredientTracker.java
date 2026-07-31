package ru.wilyfox.client.alchemy;

import net.minecraft.client.Minecraft;
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

    private final Map<Long, AlchemyIngredientSpot> spots = new LinkedHashMap<>();

    private AlchemyIngredientTracker() {
    }

    public static AlchemyIngredientTracker getInstance() {
        return INSTANCE;
    }

    public void addParticle(double x, double y, double z) {
        if (!ConfigManager.get().render.showAlchemyIngredientMarkers || !hasAlchemyBossBar()) {
            clear();
            return;
        }

        long now = System.currentTimeMillis();
        Vec3 position = new Vec3(x, y, z);
        long blockKey = BlockPos.containing(x, y, z).asLong();
        spots.put(blockKey, new AlchemyIngredientSpot(position, now));
        cleanup(now);
    }

    public List<AlchemyIngredientSpot> getActiveSpots() {
        if (!ConfigManager.get().render.showAlchemyIngredientMarkers) {
            clear();
            return List.of();
        }

        cleanup(System.currentTimeMillis());
        return List.copyOf(spots.values());
    }

    public void clear() {
        spots.clear();
    }

    public int diagnosticSpotCount() {
        cleanup(System.currentTimeMillis());
        return spots.size();
    }

    private void cleanup(long now) {
        Iterator<AlchemyIngredientSpot> iterator = spots.values().iterator();
        while (iterator.hasNext()) {
            AlchemyIngredientSpot spot = iterator.next();
            if (now - spot.createdAtMillis() > LIFETIME_MS) {
                iterator.remove();
            }
        }
    }

    private static boolean hasAlchemyBossBar() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null || !(minecraft.gui.getBossOverlay() instanceof BossHealthOverlayAccessor accessor)) {
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
