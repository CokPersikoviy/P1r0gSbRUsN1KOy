package ru.wilyfox.client.miner;

import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public record ActiveMinerInfo(
        ItemStack icon,
        int level,
        String categoryId,
        int spriteIdOffset,
        String resource,
        String status,
        long homecomingAt,
        long receivedAtNanos,
        long remainingAtReceiptMillis
) {
    public String id() {
        String category = categoryId == null ? "" : categoryId.toUpperCase(Locale.ROOT);
        return category + "_" + spriteIdOffset;
    }

    public boolean isDead() {
        String normalized = normalizedStatus();
        return normalized.contains("DEAD") || normalized.contains("DIED") || normalized.contains("KILLED");
    }

    public boolean isInTravel() {
        return "IN_TRAVEL".equals(normalizedStatus());
    }

    public boolean isComplete() {
        return isCompleteAt(System.nanoTime());
    }

    public boolean isCompleteAt(long monotonicNowNanos) {
        return "COMPLETE_TRAVEL".equals(normalizedStatus())
                || (isInTravel() && (homecomingAt <= 0L || remainingMillisAt(monotonicNowNanos) <= 0L));
    }

    public long remainingMillis() {
        return remainingMillisAt(System.nanoTime());
    }

    public long remainingMillisAt(long monotonicNowNanos) {
        if (homecomingAt <= 0L) {
            return 0L;
        }

        long elapsedNanos = monotonicNowNanos - receivedAtNanos;
        long elapsedMillis = elapsedNanos <= 0L ? 0L : TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        return remainingAtReceiptMillis - elapsedMillis;
    }

    private String normalizedStatus() {
        return status == null ? "" : status.toUpperCase(Locale.ROOT);
    }
}
