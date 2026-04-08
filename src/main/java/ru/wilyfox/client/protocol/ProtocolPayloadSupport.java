package ru.wilyfox.client.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import ru.wilyfox.client.miner.ActiveMinerInfo;
import ru.wilyfox.client.pet.ActivePetInfo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

final class ProtocolPayloadSupport {
    private ProtocolPayloadSupport() {
    }

    static List<ActivePetInfo> extractActivePets(ProtocolState state, DwStatisticInfoPacket packet) {
        String petsJson = packet.values().get("pets");
        if (petsJson == null || petsJson.isBlank()) {
            return List.of();
        }

        JsonElement parsed = JsonParser.parseString(petsJson);
        if (!parsed.isJsonArray()) {
            return List.of();
        }

        JsonArray petsArray = parsed.getAsJsonArray();
        List<ActivePetInfo> result = new ArrayList<>();

        for (JsonElement element : petsArray) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            String id = getString(object, "pet");
            if (id == null || id.isBlank()) {
                continue;
            }

            DwPetType type = state.petTypes.get(id);
            String name = type != null ? type.name() : prettifyId(id);

            result.add(new ActivePetInfo(
                    id,
                    name,
                    getInt(object, "level"),
                    getDouble(object, "exp"),
                    getDouble(object, "energy")
            ));
        }

        return result;
    }

    static List<ActiveMinerInfo> extractActiveMiners(ProtocolState state, DwStatisticInfoPacket packet) {
        String minersJson = packet.values().get("miners");
        if (minersJson == null || minersJson.isBlank()) {
            return List.of();
        }

        JsonElement parsed = JsonParser.parseString(minersJson);
        if (!parsed.isJsonArray()) {
            return List.of();
        }

        JsonArray minersArray = parsed.getAsJsonArray();
        List<ActiveMinerInfo> result = new ArrayList<>();

        for (JsonElement element : minersArray) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            JsonObject data = object.has("data") && object.get("data").isJsonObject()
                    ? object.getAsJsonObject("data")
                    : new JsonObject();
            int level = resolveMinerLevel(object, data);
            int spriteIdOffset = resolveMinerSpriteIdOffset(object, data);
            String category = getString(data, "category");

            if (!state.loggedFirstMinerPayload) {
                state.loggedFirstMinerPayload = true;
                info(LOGGER, "DW protocol: first miner object={}", object);
            }

            result.add(new ActiveMinerInfo(
                    createMinerIcon(spriteIdOffset, level, category),
                    level,
                    prettifyMinerResource(category),
                    getString(object, "status"),
                    getLong(data, "homecoming")
            ));
        }

        return result;
    }

    static boolean shouldTriggerRuneSetCooldown(ProtocolState state, Map<String, Long> timers, long now) {
        long elapsed = state.lastAbilityTimersAt > 0L ? Math.max(0L, now - state.lastAbilityTimersAt) : 0L;

        for (Map.Entry<String, Long> entry : timers.entrySet()) {
            long current = Math.max(0L, entry.getValue());
            if (current <= 0L) {
                continue;
            }

            long previousRaw = Math.max(0L, state.lastAbilityTimers.getOrDefault(entry.getKey(), 0L));
            long previousRemaining = Math.max(0L, previousRaw - elapsed);

            if (previousRemaining <= 0L || current > previousRemaining + 1_500L) {
                return true;
            }
        }

        return false;
    }

    static String formatEnergy(double energy) {
        if (Math.floor(energy) == energy) {
            return Integer.toString((int) energy);
        }

        return String.format(Locale.US, "%.1f", energy);
    }

    static String prettifyId(String id) {
        String[] parts = id.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }

        return builder.isEmpty() ? id : builder.toString();
    }

    static String prettifyMinerResource(String category) {
        if (category == null || category.isBlank()) {
            return "\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e";
        }

        return switch (category.toUpperCase(Locale.ROOT)) {
            case "MOBS" -> "\u041c\u043e\u0431\u044b";
            case "CASES" -> "\u041a\u0435\u0439\u0441\u044b";
            case "MONEY" -> "\u041c\u043e\u043d\u0435\u0442\u044b";
            case "SHARDS" -> "\u0428\u0430\u0440\u0434\u044b";
            case "BLOCKS" -> "\u0411\u043b\u043e\u043a\u0438";
            case "COLLECTIONS" -> "\u041a\u043e\u043b\u043b\u0435\u043a\u0446\u0438\u0438";
            case "ORE", "ORES" -> "\u0420\u0443\u0434\u0430";
            default -> prettifyId(category);
        };
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    static String normalizeStatisticString(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            JsonElement parsed = JsonParser.parseString(trimmed);
            if (parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isString()) {
                String unwrapped = parsed.getAsString();
                return unwrapped == null || unwrapped.isBlank() ? null : unwrapped;
            }
        } catch (Exception ignored) {
        }

        return trimmed;
    }

    static String formatRemainingMillis(int remainingMillis) {
        return formatRemainingMillis((long) remainingMillis);
    }

    static String formatRemainingMillis(long remainingMillis) {
        boolean negative = remainingMillis < 0L;
        Duration duration = Duration.ofMillis(Math.abs(remainingMillis));

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        String formatted = hours > 0
                ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);

        return negative ? "-" + formatted : formatted;
    }

    static String formatCompactMoney(double value) {
        if (value >= 1_000_000_000_000D) {
            return String.format(Locale.US, "%.2fT", value / 1_000_000_000_000D);
        }
        if (value >= 1_000_000_000D) {
            return String.format(Locale.US, "%.2fB", value / 1_000_000_000D);
        }
        if (value >= 1_000_000D) {
            return String.format(Locale.US, "%.2fM", value / 1_000_000D);
        }
        if (value >= 1_000D) {
            return String.format(Locale.US, "%.2fK", value / 1_000D);
        }

        return String.format(Locale.US, "%.2f", value);
    }

    static String formatCompactMultiplier(double value) {
        return String.format(Locale.US, "%.2f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : null;
    }

    static int getInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsInt() : 0;
    }

    static long getLong(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsLong() : 0L;
    }

    static double getDouble(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsDouble() : 0.0D;
    }

    static int getInt(Map<String, String> values, String key) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return 0;
        }

        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    static double getDouble(Map<String, String> values, String key) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return 0.0D;
        }

        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception ignored) {
            return 0.0D;
        }
    }

    private static int resolveMinerLevel(JsonObject object, JsonObject data) {
        int level = getInt(object, "level");
        if (level > 0) {
            return level;
        }

        level = getInt(data, "level");
        if (level > 0) {
            return level;
        }

        level = getInt(object, "minerLevel");
        if (level > 0) {
            return level;
        }

        level = getInt(data, "minerLevel");
        if (level > 0) {
            return level;
        }

        int experience = getInt(object, "exp");
        if (experience >= 0) {
            return deriveMinerLevel(experience);
        }

        return 0;
    }

    private static int resolveMinerSpriteIdOffset(JsonObject object, JsonObject data) {
        int spriteIdOffset = getInt(object, "spriteIdOffset");
        if (spriteIdOffset >= 0) {
            return spriteIdOffset;
        }

        spriteIdOffset = getInt(data, "spriteIdOffset");
        if (spriteIdOffset >= 0) {
            return spriteIdOffset;
        }

        return 0;
    }

    private static ItemStack createMinerIcon(int spriteIdOffset, int level, String category) {
        ItemStack stack = new ItemStack(Items.COMMAND_BLOCK);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) (76 + Math.max(0, spriteIdOffset))), List.of(), List.of(), List.of()));
        String label = level > 0
                ? "\u0428\u0430\u0445\u0442\u0435\u0440 " + prettifyMinerResource(category) + " [" + level + "]"
                : "\u0428\u0430\u0445\u0442\u0435\u0440 " + prettifyMinerResource(category);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(label));
        return stack;
    }

    private static int deriveMinerLevel(int experience) {
        int remainingExp = Math.max(0, experience);
        int level = 1;
        int nextLevelCost = 30;

        while (remainingExp > nextLevelCost) {
            remainingExp -= nextLevelCost;
            level++;
            nextLevelCost = (int) (nextLevelCost * 1.4D);
            if (nextLevelCost <= 0) {
                break;
            }
        }

        return Math.max(1, level);
    }
}
