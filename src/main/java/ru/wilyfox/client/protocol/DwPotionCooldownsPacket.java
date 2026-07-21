package ru.wilyfox.client.protocol;

import java.util.Map;

public record DwPotionCooldownsPacket(Map<Integer, Long> cooldowns) {
}
