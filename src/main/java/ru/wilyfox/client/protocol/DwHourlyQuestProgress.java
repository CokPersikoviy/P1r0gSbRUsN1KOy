package ru.wilyfox.client.protocol;

public record DwHourlyQuestProgress(int id, int progress, long expiresAtMillis) {
    public long remainingMillis() {
        return Math.max(0L, expiresAtMillis - System.currentTimeMillis());
    }
}
