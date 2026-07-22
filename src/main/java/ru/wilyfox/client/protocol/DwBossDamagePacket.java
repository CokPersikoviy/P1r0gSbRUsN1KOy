package ru.wilyfox.client.protocol;

public record DwBossDamagePacket(
        String bossId,
        int damage
) {
}
