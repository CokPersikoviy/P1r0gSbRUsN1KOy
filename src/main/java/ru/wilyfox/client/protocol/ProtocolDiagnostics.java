package ru.wilyfox.client.protocol;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class ProtocolDiagnostics {
    private static final int MAX_RECENT_ANOMALIES = 20;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault());

    private final Map<String, Integer> receivedByType = new LinkedHashMap<>();
    private final Map<String, Integer> failedByType = new LinkedHashMap<>();
    private final Deque<String> recentAnomalies = new ArrayDeque<>();

    private int unreadableEnvelopeCount;
    private int unknownTypeCount;
    private int decodeFailureCount;

    void onPayloadReceived(String typeId) {
        receivedByType.merge(typeId, 1, Integer::sum);
    }

    void onUnreadableEnvelope(int length) {
        unreadableEnvelopeCount++;
        addAnomaly("unreadable envelope bytes=" + length);
    }

    void onUnknownType(String typeId, int bodyLength) {
        unknownTypeCount++;
        addAnomaly("unknown type=" + typeId + " bodyBytes=" + bodyLength);
    }

    void onDecodeFailure(String typeId, int bodyLength, Exception exception) {
        decodeFailureCount++;
        failedByType.merge(typeId, 1, Integer::sum);

        String message = exception != null && exception.getMessage() != null && !exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() + ": " + exception.getMessage()
                : exception != null ? exception.getClass().getSimpleName() : "no exception details";
        addAnomaly("decode failure type=" + typeId + " bodyBytes=" + bodyLength + " reason=" + message);
    }

    List<String> buildStatsLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Received payloads: " + total(receivedByType));
        lines.add("Unreadable envelopes: " + unreadableEnvelopeCount);
        lines.add("Unknown subchannels: " + unknownTypeCount);
        lines.add("Decode failures: " + decodeFailureCount);

        if (!receivedByType.isEmpty()) {
            lines.add("Top payloads: " + topEntries(receivedByType));
        }
        if (!failedByType.isEmpty()) {
            lines.add("Failures by type: " + topEntries(failedByType));
        }

        if (recentAnomalies.isEmpty()) {
            lines.add("Recent anomalies: none");
        } else {
            lines.add("Recent anomalies: " + recentAnomalies.size());
        }

        return lines;
    }

    List<String> buildAnomalyLines() {
        if (recentAnomalies.isEmpty()) {
            return List.of("Protocol anomalies: none");
        }

        return recentAnomalies.stream().toList();
    }

    void reset() {
        receivedByType.clear();
        failedByType.clear();
        recentAnomalies.clear();
        unreadableEnvelopeCount = 0;
        unknownTypeCount = 0;
        decodeFailureCount = 0;
    }

    private void addAnomaly(String message) {
        String timestamped = TIME_FORMATTER.format(Instant.now()) + " " + message;
        recentAnomalies.addLast(timestamped);
        while (recentAnomalies.size() > MAX_RECENT_ANOMALIES) {
            recentAnomalies.removeFirst();
        }
    }

    private static int total(Map<String, Integer> values) {
        return values.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static String topEntries(Map<String, Integer> values) {
        return values.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(6)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }
}
