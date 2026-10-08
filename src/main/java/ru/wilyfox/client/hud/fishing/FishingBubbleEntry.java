package ru.wilyfox.client.hud.fishing;

import net.minecraft.world.phys.Vec3;

public record FishingBubbleEntry(Vec3 position, long timestamp, int particleCount) {
    public FishingBubbleEntry(Vec3 position, long timestamp) {
        this(position, timestamp, 1);
    }
}
