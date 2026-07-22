package ru.wilyfox.client.alchemy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;
import ru.wilyfox.bridge.BossHealthOverlayAccessor;
import ru.wilyfox.client.hud.config.ConfigManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class AlchemyIngredientTracker {
    private static final AlchemyIngredientTracker INSTANCE = new AlchemyIngredientTracker();
    private static final long LIFETIME_MS = 2_000L;
    private static final double DUPLICATE_DISTANCE_SQUARED = 0.04;

    private final List<AlchemyIngredientSpot> spots = new ArrayList<>();

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
        for (int i = 0; i < spots.size(); i++) {
            AlchemyIngredientSpot spot = spots.get(i);
            if (spot.center().distanceToSqr(position) <= DUPLICATE_DISTANCE_SQUARED) {
                spots.set(i, new AlchemyIngredientSpot(position, now));
                cleanup(now);
                return;
            }
        }

        spots.add(new AlchemyIngredientSpot(position, now));
        cleanup(now);
    }

    public List<AlchemyIngredientSpot> getActiveSpots() {
        if (!ConfigManager.get().render.showAlchemyIngredientMarkers) {
            clear();
            return List.of();
        }

        cleanup(System.currentTimeMillis());
        return List.copyOf(spots);
    }

    public void clear() {
        spots.clear();
    }

    private void cleanup(long now) {
        Iterator<AlchemyIngredientSpot> iterator = spots.iterator();
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
