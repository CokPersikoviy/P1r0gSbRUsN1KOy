package ru.wilyfox.client.hud.fishing;

import net.minecraft.world.phys.Vec3;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class FishingSpotTracker {
    private static final FishingSpotTracker INSTANCE = new FishingSpotTracker();
    private static final long LIFETIME_MS = 1_000L;
    private static final double Y_OFFSET = 0.25D;
    private static final double DEDUPLICATION_DISTANCE_SQUARED = 0.04D;

    private final List<FishingBubbleEntry> particles = new ArrayList<>();

    private FishingSpotTracker() {
    }

    public static FishingSpotTracker getInstance() {
        return INSTANCE;
    }

    public boolean shouldTrackParticles() {
        return ConfigManager.get().fishing.showFishingMarkers
                && DiamondWorldProtocolClient.isCurrentFishingLocation();
    }

    public boolean shouldDebugParticles() {
        return DiamondWorldProtocolClient.isCurrentFishingLocation();
    }

    public String getCurrentFishingLocationId() {
        if (!DiamondWorldProtocolClient.isCurrentFishingLocation()) {
            return null;
        }
        return DiamondWorldProtocolClient.getCurrentGameLocationData().normalizedId();
    }

    public synchronized void addBubble(double x, double y, double z) {
        if (!shouldTrackParticles()) {
            clear();
            return;
        }

        long now = System.currentTimeMillis();
        Vec3 position = new Vec3(x, y + Y_OFFSET, z);
        cleanup(now);

        for (int i = 0; i < particles.size(); i++) {
            FishingBubbleEntry entry = particles.get(i);
            if (entry.position().distanceToSqr(position) <= DEDUPLICATION_DISTANCE_SQUARED) {
                particles.set(i, new FishingBubbleEntry(position, now));
                return;
            }
        }
        particles.add(new FishingBubbleEntry(position, now));
    }

    public synchronized List<FishingBubbleEntry> getActiveBubbles() {
        if (!shouldTrackParticles()) {
            clear();
            return List.of();
        }
        cleanup(System.currentTimeMillis());
        return List.copyOf(particles);
    }

    public synchronized List<FishingSpot> getActiveSpots() {
        return getActiveBubbles().stream()
                .map(entry -> new FishingSpot(entry.position(), 45, entry.timestamp()))
                .toList();
    }

    private void cleanup(long now) {
        Iterator<FishingBubbleEntry> iterator = particles.iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().timestamp() > LIFETIME_MS) {
                iterator.remove();
            }
        }
    }

    public synchronized void clear() {
        particles.clear();
    }
}
