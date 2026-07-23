package ru.wilyfox.client.hud.fishing;

import net.minecraft.world.phys.Vec3;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class FishingSpotTracker {
    private static final FishingSpotTracker INSTANCE = new FishingSpotTracker();
    private static final long LIFETIME_MS = 1_000L;
    private static final double Y_OFFSET = 0.25D;
    private static final double GRID_SIZE = 0.5D;
    private static final int MIN_BUBBLES_PER_SPOT = 3;

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
        if (!shouldTrackParticles()) {
            clear();
            return List.of();
        }

        cleanup(System.currentTimeMillis());
        return clusterBubbles(particles);
    }

    static List<FishingSpot> clusterBubbles(List<FishingBubbleEntry> bubbles) {
        if (bubbles == null || bubbles.isEmpty()) {
            return List.of();
        }

        Map<GridPos, CellData> cells = new HashMap<>();
        for (FishingBubbleEntry bubble : bubbles) {
            GridPos cell = GridPos.fromVec(bubble.position());
            cells.computeIfAbsent(cell, ignored -> new CellData())
                    .add(bubble.position(), bubble.timestamp());
        }

        List<FishingSpot> spots = new ArrayList<>();
        Set<GridPos> visited = new HashSet<>();

        for (GridPos start : cells.keySet()) {
            if (!visited.add(start)) {
                continue;
            }

            Queue<GridPos> pending = new ArrayDeque<>();
            pending.add(start);

            CellData cluster = new CellData();
            while (!pending.isEmpty()) {
                GridPos current = pending.remove();
                cluster.add(cells.get(current));

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }

                            GridPos neighbor = current.offset(dx, dy, dz);
                            if (cells.containsKey(neighbor) && visited.add(neighbor)) {
                                pending.add(neighbor);
                            }
                        }
                    }
                }
            }

            if (cluster.count >= MIN_BUBBLES_PER_SPOT) {
                spots.add(cluster.toSpot());
            }
        }

        return List.copyOf(spots);
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

    private static final class CellData {
        private int count;
        private long latestTimestamp;
        private double sumX;
        private double sumY;
        private double sumZ;

        private void add(Vec3 position, long timestamp) {
            count++;
            latestTimestamp = Math.max(latestTimestamp, timestamp);
            sumX += position.x;
            sumY += position.y;
            sumZ += position.z;
        }

        private void add(CellData other) {
            count += other.count;
            latestTimestamp = Math.max(latestTimestamp, other.latestTimestamp);
            sumX += other.sumX;
            sumY += other.sumY;
            sumZ += other.sumZ;
        }

        private FishingSpot toSpot() {
            return new FishingSpot(
                    new Vec3(sumX / count, sumY / count, sumZ / count),
                    count,
                    latestTimestamp
            );
        }
    }

    private record GridPos(int x, int y, int z) {
        private static GridPos fromVec(Vec3 position) {
            return new GridPos(
                    (int) Math.floor(position.x / GRID_SIZE),
                    (int) Math.floor(position.y / GRID_SIZE),
                    (int) Math.floor(position.z / GRID_SIZE)
            );
        }

        private GridPos offset(int dx, int dy, int dz) {
            return new GridPos(x + dx, y + dy, z + dz);
        }
    }
}
