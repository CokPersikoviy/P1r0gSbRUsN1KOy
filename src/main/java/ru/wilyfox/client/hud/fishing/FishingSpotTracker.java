package ru.wilyfox.client.hud.fishing;

import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import ru.wilyfox.client.debug.DebugLogger;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class FishingSpotTracker {
    private static final FishingSpotTracker INSTANCE = new FishingSpotTracker();
    private static final long LIFETIME_MS = 1_000L;
    private static final double Y_OFFSET = 0.25D;
    private static final double GRID_SIZE = 0.5D;
    private static final int MAX_TRACKED_PACKETS = 2_048;

    private final ArrayDeque<FishingBubbleEntry> particles = new ArrayDeque<>();
    private String trackedLocationId;

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
        return DebugLogger.isEnabled() && DiamondWorldProtocolClient.isCurrentFishingLocation();
    }

    public String getCurrentFishingLocationId() {
        if (!DiamondWorldProtocolClient.isCurrentFishingLocation()) {
            return null;
        }
        return DiamondWorldProtocolClient.getCurrentGameLocationData().normalizedId();
    }

    public synchronized void onParticlePacket(ClientboundLevelParticlesPacket packet) {
        if (!shouldTrackParticles()) {
            clear();
            return;
        }
        if (packet == null || !FishingParticleTypes.isFishingSpot(packet.getParticle())) return;
        refreshLocation();

        long now = System.currentTimeMillis();
        Vec3 position = new Vec3(packet.getX(), packet.getY() + Y_OFFSET, packet.getZ());
        cleanup(now);
        if (particles.size() >= MAX_TRACKED_PACKETS) particles.removeFirst();
        // Count zero is a valid directional particle packet. Cap marker strength, not packet handling.
        int strength = Math.max(1, Math.min(45, packet.getCount()));
        particles.addLast(new FishingBubbleEntry(position, now, strength));
    }

    public synchronized List<FishingBubbleEntry> getActiveBubbles() {
        if (!shouldTrackParticles()) {
            clear();
            return List.of();
        }
        refreshLocation();
        cleanup(System.currentTimeMillis());
        return List.copyOf(particles);
    }

    public synchronized List<FishingSpot> getActiveSpots() {
        if (!shouldTrackParticles()) {
            clear();
            return List.of();
        }

        refreshLocation();
        cleanup(System.currentTimeMillis());
        return clusterBubbles(particles);
    }

    static List<FishingSpot> clusterBubbles(Collection<FishingBubbleEntry> bubbles) {
        if (bubbles == null || bubbles.isEmpty()) {
            return List.of();
        }

        Map<GridPos, CellData> cells = new HashMap<>();
        for (FishingBubbleEntry bubble : bubbles) {
            GridPos cell = GridPos.fromVec(bubble.position());
            cells.computeIfAbsent(cell, ignored -> new CellData())
                    .add(bubble.position(), bubble.timestamp(), bubble.particleCount());
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

            // A recognized server packet already identifies a spot, even with one rendered particle.
            spots.add(cluster.toSpot());
        }

        return List.copyOf(spots);
    }

    private void cleanup(long now) {
        while (!particles.isEmpty() && now - particles.getFirst().timestamp() > LIFETIME_MS) particles.removeFirst();
    }

    public synchronized void clear() {
        particles.clear();
        trackedLocationId = null;
    }

    private void refreshLocation() {
        String locationId = getCurrentFishingLocationId();
        if (!java.util.Objects.equals(trackedLocationId, locationId)) {
            clear();
            trackedLocationId = locationId;
        }
    }

    public synchronized int diagnosticParticleCount() {
        cleanup(System.currentTimeMillis());
        return particles.size();
    }

    private static final class CellData {
        private int count;
        private long latestTimestamp;
        private double sumX;
        private double sumY;
        private double sumZ;

        private void add(Vec3 position, long timestamp, int particleCount) {
            count += particleCount;
            latestTimestamp = Math.max(latestTimestamp, timestamp);
            sumX += position.x * particleCount;
            sumY += position.y * particleCount;
            sumZ += position.z * particleCount;
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
