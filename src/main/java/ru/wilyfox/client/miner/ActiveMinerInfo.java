package ru.wilyfox.client.miner;

import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public record ActiveMinerInfo(
        ItemStack icon,
        int level,
        String resource,
        String status,
        long homecomingAt
) {
    public boolean isDead() {
        String normalized = normalizedStatus();
        return normalized.contains("DEAD") || normalized.contains("DIED") || normalized.contains("KILLED");
    }

    public boolean isInTravel() {
        return "IN_TRAVEL".equals(normalizedStatus());
    }

    public boolean isComplete(long now) {
        return "COMPLETE_TRAVEL".equals(normalizedStatus())
                || (isInTravel() && (homecomingAt <= 0L || homecomingAt <= now));
    }

    private String normalizedStatus() {
        return status == null ? "" : status.toUpperCase(Locale.ROOT);
    }
}
