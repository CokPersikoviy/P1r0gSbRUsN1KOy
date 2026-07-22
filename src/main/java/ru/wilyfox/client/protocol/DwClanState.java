package ru.wilyfox.client.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record DwClanState(String name, List<String> members, List<String> bossIds) {
    public DwClanState {
        name = name != null ? name : "";
        members = members != null ? List.copyOf(members) : List.of();
        bossIds = bossIds != null ? List.copyOf(bossIds) : List.of();
    }

    public static DwClanState empty() {
        return new DwClanState("", List.of(), List.of());
    }

    public DwClanState applyPartial(Map<String, String> values) {
        String updatedName = name;
        List<String> updatedMembers = members;
        List<String> updatedBossIds = bossIds;

        if (values.containsKey("name")) {
            try {
                updatedName = parseString(values.get("name"));
            } catch (RuntimeException ignored) {
                // A malformed field must not erase the last valid protocol value.
            }
        }
        if (values.containsKey("members")) {
            try {
                updatedMembers = parseStringList(values.get("members"));
            } catch (RuntimeException ignored) {
                // Preserve the last valid field independently from the rest of the packet.
            }
        }
        if (values.containsKey("bosses")) {
            try {
                updatedBossIds = parseStringList(values.get("bosses"));
            } catch (RuntimeException ignored) {
                // Preserve the last valid field independently from the rest of the packet.
            }
        }

        return new DwClanState(updatedName, updatedMembers, updatedBossIds);
    }

    public boolean containsMember(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }

        String normalized = playerName.trim().toLowerCase(Locale.ROOT);
        return members.stream().anyMatch(member -> member.toLowerCase(Locale.ROOT).equals(normalized));
    }

    private static String parseString(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected a JSON string");
        }
        return element.getAsString();
    }

    private static List<String> parseStringList(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Expected a JSON string array");
        }

        JsonArray array = element.getAsJsonArray();
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement entry : array) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Expected a JSON string array");
            }
            values.add(entry.getAsString());
        }
        return values;
    }
}
