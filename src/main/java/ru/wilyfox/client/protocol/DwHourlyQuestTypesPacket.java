package ru.wilyfox.client.protocol;

import java.util.Map;

public record DwHourlyQuestTypesPacket(Map<Integer, DwHourlyQuestType> types) {
}
