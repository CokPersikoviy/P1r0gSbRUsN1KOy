package ru.wilyfox.client.chat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reassembles a bounded multipart chat payload. One assembler instance represents one protocol type,
 * and accepts at most one in-flight message per sender.
 */
public final class MultipartChatMessageAssembler {
    private final int maxActiveSenders;
    private final int maxParts;
    private final int maxPartLength;
    private final int maxPayloadLength;
    private final long ttlMillis;
    private final Map<String, IncomingMessage> incoming = new HashMap<>();

    public MultipartChatMessageAssembler(
            int maxActiveSenders,
            int maxParts,
            int maxPartLength,
            int maxPayloadLength,
            long ttlMillis
    ) {
        this.maxActiveSenders = Math.max(1, maxActiveSenders);
        this.maxParts = Math.max(1, maxParts);
        this.maxPartLength = Math.max(1, maxPartLength);
        this.maxPayloadLength = Math.max(this.maxPartLength, maxPayloadLength);
        this.ttlMillis = Math.max(1L, ttlMillis);
    }

    public synchronized Result accept(
            String sender,
            String messageId,
            int partIndex,
            int totalParts,
            String payloadPart,
            long nowMillis
    ) {
        pruneExpired(nowMillis);

        String senderKey = normalizeSender(sender);
        if (senderKey == null
                || messageId == null || messageId.isBlank() || messageId.length() > 64
                || totalParts < 1 || totalParts > maxParts
                || partIndex < 1 || partIndex > totalParts
                || payloadPart == null || payloadPart.length() > maxPartLength) {
            return Result.rejected();
        }

        IncomingMessage message = incoming.get(senderKey);
        if (message == null) {
            if (incoming.size() >= maxActiveSenders) {
                return Result.rejected();
            }
            message = new IncomingMessage(messageId, totalParts, nowMillis);
            incoming.put(senderKey, message);
        } else if (!message.messageId.equals(messageId) || message.parts.length != totalParts) {
            return Result.rejected();
        }

        if (!message.put(partIndex, payloadPart, maxPayloadLength)) {
            return Result.rejected();
        }
        if (!message.isComplete()) {
            return Result.accepted();
        }

        incoming.remove(senderKey);
        return Result.complete(message.join());
    }

    public synchronized int size() {
        return incoming.size();
    }

    public synchronized void clear() {
        incoming.clear();
    }

    private void pruneExpired(long nowMillis) {
        incoming.values().removeIf(message -> nowMillis - message.createdAtMillis >= ttlMillis);
    }

    private static String normalizeSender(String sender) {
        if (sender == null) {
            return null;
        }
        String normalized = sender.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() || normalized.length() > 64 ? null : normalized;
    }

    public record Result(Status status, String payload) {
        private static Result accepted() {
            return new Result(Status.ACCEPTED, null);
        }

        private static Result complete(String payload) {
            return new Result(Status.COMPLETE, payload);
        }

        private static Result rejected() {
            return new Result(Status.REJECTED, null);
        }
    }

    public enum Status {
        ACCEPTED,
        COMPLETE,
        REJECTED
    }

    private static final class IncomingMessage {
        private final String messageId;
        private final String[] parts;
        private final long createdAtMillis;
        private int payloadLength;

        private IncomingMessage(String messageId, int totalParts, long createdAtMillis) {
            this.messageId = messageId;
            this.parts = new String[totalParts];
            this.createdAtMillis = createdAtMillis;
        }

        private boolean put(int partIndex, String payloadPart, int maxPayloadLength) {
            int index = partIndex - 1;
            String existing = parts[index];
            if (existing != null) {
                return existing.equals(payloadPart);
            }
            if (payloadLength + payloadPart.length() > maxPayloadLength) {
                return false;
            }
            parts[index] = payloadPart;
            payloadLength += payloadPart.length();
            return true;
        }

        private boolean isComplete() {
            for (String part : parts) {
                if (part == null) {
                    return false;
                }
            }
            return true;
        }

        private String join() {
            return String.join("", parts);
        }
    }
}
