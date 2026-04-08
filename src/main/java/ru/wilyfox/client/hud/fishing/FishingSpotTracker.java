package ru.wilyfox.client.hud.fishing;

import net.minecraft.world.phys.Vec3;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.util.*;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

public final class FishingSpotTracker {
    private static final FishingSpotTracker INSTANCE = new FishingSpotTracker();

    private static final long LIFETIME_MS = 1500L;

    // Размер ячейки сетки в блоках
    private static final double GRID_SIZE = 0.5;

    // Минимум пузырьков в объединённом споте
    private static final int MIN_BUBBLES_PER_SPOT = 3;

    private final List<FishingBubbleEntry> bubbles = new ArrayList<>();
    private final Set<String> loggedUnknownLocations = new HashSet<>();

    private FishingSpotTracker() {
    }

    public static FishingSpotTracker getInstance() {
        return INSTANCE;
    }

    public boolean shouldTrackParticles() {
        return isFishingLocation();
    }

    public boolean shouldDebugParticles() {
        return getCurrentFishingLocationId() != null;
    }

    public String getCurrentFishingLocationId() {
        String currentLocation = DiamondWorldProtocolClient.getCurrentGameLocation();
        if (currentLocation == null || currentLocation.isBlank()) {
            return null;
        }

        return currentLocation.trim().toLowerCase(Locale.ROOT);
    }

    public void addBubble(double x, double y, double z) {
        if (!isFishingLocation()) {
            clear();
            return;
        }

        long now = System.currentTimeMillis();
        bubbles.add(new FishingBubbleEntry(new Vec3(x, y, z), now));
        cleanup(now);
    }

    public List<FishingBubbleEntry> getActiveBubbles() {
        if (!isFishingLocation()) {
            clear();
            return List.of();
        }

        cleanup(System.currentTimeMillis());
        return List.copyOf(bubbles);
    }

    public List<FishingSpot> getActiveSpots() {
        if (!isFishingLocation()) {
            clear();
            return List.of();
        }

        cleanup(System.currentTimeMillis());

        Map<GridPos, CellData> cells = new HashMap<>();

        for (FishingBubbleEntry bubble : bubbles) {
            GridPos cell = GridPos.fromVec(bubble.position(), GRID_SIZE);
            cells.computeIfAbsent(cell, __ -> new CellData()).add(bubble.position(), bubble.timestamp());
        }

        List<FishingSpot> result = new ArrayList<>();
        Set<GridPos> visited = new HashSet<>();

        for (GridPos start : cells.keySet()) {
            if (visited.contains(start)) {
                continue;
            }

            Queue<GridPos> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start);

            int totalCount = 0;
            long latestTimestamp = 0L;
            double sumX = 0.0;
            double sumY = 0.0;
            double sumZ = 0.0;

            while (!queue.isEmpty()) {
                GridPos current = queue.poll();
                CellData data = cells.get(current);
                if (data == null) {
                    continue;
                }

                totalCount += data.count;
                latestTimestamp = Math.max(latestTimestamp, data.latestTimestamp);

                sumX += data.sumX;
                sumY += data.sumY;
                sumZ += data.sumZ;

                for (GridPos neighbor : current.neighbors()) {
                    if (cells.containsKey(neighbor) && visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }

            if (totalCount < MIN_BUBBLES_PER_SPOT) {
                continue;
            }

            Vec3 center = new Vec3(
                    sumX / totalCount,
                    sumY / totalCount,
                    sumZ / totalCount
            );

            result.add(new FishingSpot(center, totalCount, latestTimestamp));
        }

        return result;
    }

    private void cleanup(long now) {
        Iterator<FishingBubbleEntry> it = bubbles.iterator();
        while (it.hasNext()) {
            FishingBubbleEntry entry = it.next();
            if (now - entry.timestamp() > LIFETIME_MS) {
                it.remove();
            }
        }
    }

    public void clear() {
        bubbles.clear();
    }

    private boolean isFishingLocation() {
        if (DiamondWorldProtocolClient.isCurrentFishingLocation()) {
            return true;
        }

        String currentLocation = getCurrentFishingLocationId();
        if (currentLocation == null) {
            return false;
        }

        if (loggedUnknownLocations.add(currentLocation)) {
            info(LOGGER, "Fishing location debug: unknown location={}", currentLocation);
        }

        return false;
    }

    private static final class CellData {
        private int count;
        private long latestTimestamp;
        private double sumX;
        private double sumY;
        private double sumZ;

        private void add(Vec3 pos, long timestamp) {
            count++;
            latestTimestamp = Math.max(latestTimestamp, timestamp);
            sumX += pos.x;
            sumY += pos.y;
            sumZ += pos.z;
        }
    }

    private record GridPos(int x, int y, int z) {
        static GridPos fromVec(Vec3 pos, double grid) {
            return new GridPos(
                    (int) Math.floor(pos.x / grid),
                    (int) Math.floor(pos.y / grid),
                    (int) Math.floor(pos.z / grid)
            );
        }

        List<GridPos> neighbors() {
            List<GridPos> result = new ArrayList<>(26);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }

                        result.add(new GridPos(x + dx, y + dy, z + dz));
                    }
                }
            }

            return result;
        }
    }
}
