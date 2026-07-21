package ru.wilyfox.client.protocol;

public record DwBossType(
        String id,
        String name,
        String material,
        int level,
        int customModelData,
        int capturePoints,
        boolean raid
) {
}
