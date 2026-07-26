package ru.wilyfox.client.chat;

import java.util.List;

final class MarketAutoMessageState {
    static final long SEND_GRACE_MS = 1_000L;

    private long consumedCooldownRevision;
    private int nextMessageIndex;

    String nextMessage(List<String> messages, long cooldownEndsAt, long cooldownRevision, long now) {
        if (messages.isEmpty() || cooldownEndsAt < 0L || cooldownRevision <= 0L) {
            return null;
        }
        if (cooldownRevision <= consumedCooldownRevision || now < cooldownEndsAt + SEND_GRACE_MS) {
            return null;
        }

        String message = messages.get(Math.floorMod(nextMessageIndex, messages.size()));
        return message.startsWith("$") ? message : "$" + message;
    }

    void markQueued(long cooldownRevision) {
        consumedCooldownRevision = cooldownRevision;
        nextMessageIndex++;
    }

    void reset() {
        consumedCooldownRevision = 0L;
        nextMessageIndex = 0;
    }
}
