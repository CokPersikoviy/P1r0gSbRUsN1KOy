package ru.wilyfox.client.protocol;

import java.util.Map;

public record DwHourlyQuestInfoPacket(Map<Integer, Entry> quests) {
    public record Entry(int id, int progress, long remainedMillis) {
    }
}
