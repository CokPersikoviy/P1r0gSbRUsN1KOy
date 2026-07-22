package ru.wilyfox.client.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import ru.wilyfox.client.miner.ActiveMinerInfo;
import ru.wilyfox.client.pet.ActivePetInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

final class ProtocolPayloadSupport {
    private ProtocolPayloadSupport() {
    }

    static Optional<List<ActivePetInfo>> extractActivePets(ProtocolState state, DwStatisticInfoPacket packet) {
        String petsJson = packet.values().get("pets");
        if (petsJson == null) {
            return Optional.empty();
        }
        if (petsJson.isBlank()) {
            throw new IllegalArgumentException("statisticinfo pets value is blank");
        }

        JsonElement parsed = JsonParser.parseString(petsJson);
        if (parsed.isJsonNull()) {
            return Optional.empty();
        }

        JsonArray petsArray;
        if (parsed.isJsonArray()) {
            petsArray = parsed.getAsJsonArray();
        } else if (parsed.isJsonObject()) {
            petsArray = new JsonArray();
            petsArray.add(parsed);
        } else {
            throw new IllegalArgumentException("statisticinfo pets value must be a JSON array or object");
        }
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

            ActivePetInfo pet = new ActivePetInfo(
                    id,
                    prettifyId(id),
                    ItemStack.EMPTY,
                    getInt(object, "level"),
                    getDouble(object, "exp"),
                    getDouble(object, "energy"),
                    false
            );
            result.add(enrichActivePet(state, pet));
        }

        return Optional.of(result);
    }

    static Optional<List<ActiveMinerInfo>> extractActiveMiners(ProtocolState state, DwStatisticInfoPacket packet) {
        String minersJson = packet.values().get("miners");
        if (minersJson == null) {
            return Optional.empty();
        }
        if (minersJson.isBlank()) {
            throw new IllegalArgumentException("statisticinfo miners value is blank");
        }

        JsonElement parsed = JsonParser.parseString(minersJson);
        if (parsed.isJsonNull()) {
            return Optional.empty();
        }

        JsonArray minersArray;
        if (parsed.isJsonArray()) {
            minersArray = parsed.getAsJsonArray();
        } else if (parsed.isJsonObject()) {
            minersArray = new JsonArray();
            minersArray.add(parsed);
        } else {
            throw new IllegalArgumentException("statisticinfo miners value must be a JSON array or object");
        }
        List<ActiveMinerInfo> result = new ArrayList<>();
        long wallNow = System.currentTimeMillis();
        long monotonicNow = System.nanoTime();

        for (JsonElement element : minersArray) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            JsonObject data = object.has("data") && object.get("data").isJsonObject()
                    ? object.getAsJsonObject("data")
                    : new JsonObject();
            int level = deriveMinerLevel(getInt(object, "exp"));
            int spriteIdOffset = Math.max(0, getInt(object, "spriteIdOffset"));
            String category = getString(data, "category");
            long homecomingAt = getInstantMillis(data, "homecoming");

            if (!state.loggedFirstMinerPayload) {
                state.loggedFirstMinerPayload = true;
                info(LOGGER, "DW protocol: first miner object={}", object);
            }

            result.add(new ActiveMinerInfo(
                    createMinerIcon(spriteIdOffset, level, category),
                    level,
                    category,
                    spriteIdOffset,
                    prettifyMinerResource(category),
                    getString(object, "status"),
                    homecomingAt,
                    monotonicNow,
                    homecomingAt <= 0L ? 0L : homecomingAt - wallNow
            ));
        }

        return Optional.of(result);
    }

    // Abilities that DO NOT lock rune-set swapping when used (their runes read "не накладывает КД смены
    // сета рун"). Matched by protocol id (confirmed from device logs) OR by a display-name fragment via
    // abilityTypes, so abilities whose id we couldn't confirm still resolve by their in-game name.
    private static final Set<String> SWAP_CD_EXEMPT_IDS =
            Set.of("SERAPHIM", "ILLUSIONER", "TURTLE", "PHOENIX");
    private static final Set<String> SWAP_CD_EXEMPT_NAME_FRAGMENTS =
            Set.of("серафим", "иллюз", "черепах", "феникс", "титан", "божеств", "темпус");

    private static boolean isSwapCdExempt(ProtocolState state, String abilityId) {
        if (abilityId != null && SWAP_CD_EXEMPT_IDS.contains(abilityId)) {
            return true;
        }
        String name = resolveAbilityName(state, abilityId);
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String fragment : SWAP_CD_EXEMPT_NAME_FRAGMENTS) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveAbilityName(ProtocolState state, String abilityId) {
        if (state == null || state.abilityTypes == null || abilityId == null) {
            return null;
        }
        DwAbilityType type = state.abilityTypes.get(abilityId);
        if (type != null) {
            return type.name();
        }
        for (DwAbilityType candidate : state.abilityTypes.values()) {
            if (abilityId.equals(candidate.id())) {
                return candidate.name();
            }
        }
        return null;
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
                if (isSwapCdExempt(state, entry.getKey())) {
                    continue; // this ability's use does not lock rune-set swapping
                }
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

    static ActivePetInfo enrichActivePet(ProtocolState state, ActivePetInfo pet) {
        DwPetType type = state.petTypes.get(pet.id());
        if (type == null) {
            return new ActivePetInfo(
                    pet.id(),
                    prettifyId(pet.id()),
                    ItemStack.EMPTY,
                    pet.level(),
                    pet.exp(),
                    pet.energy(),
                    false
            );
        }

        return new ActivePetInfo(
                pet.id(),
                type.name(),
                createPetIcon(type),
                pet.level(),
                pet.exp(),
                pet.energy(),
                true
        );
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

    static DwGameLocation parseGameLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            JsonElement parsed = JsonParser.parseString(value.trim());
            if (!parsed.isJsonPrimitive() || !parsed.getAsJsonPrimitive().isString()) {
                return null;
            }
            return DiamondWorldProtocolClient.createGameLocation(parsed.getAsString());
        } catch (Exception ignored) {
            return null;
        }
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

    static long getInstantMillis(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return 0L;
        }
        if (element.getAsJsonPrimitive().isNumber()) {
            return element.getAsLong();
        }
        return Instant.parse(element.getAsString()).toEpochMilli();
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
            nextLevelCost = (int) (nextLevelCost * 1.5D);
            if (nextLevelCost <= 0) {
                break;
            }
        }

        return Math.max(1, level);
    }

    private static ItemStack createPetIcon(DwPetType type) {
        ResourceLocation location = resolveItemLocation(type.material());
        ItemStack stack = location == null
                ? new ItemStack(Items.BONE)
                : new ItemStack(BuiltInRegistries.ITEM.getValue(location));
        if (stack.isEmpty()) {
            stack = new ItemStack(Items.BONE);
        }
        if (type.customModelData() > 0) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) type.customModelData()), List.of(), List.of(), List.of()));
        }
        return stack;
    }

    private static ResourceLocation resolveItemLocation(String material) {
        if (material == null || material.isBlank()) {
            return null;
        }

        ResourceLocation direct = ResourceLocation.tryParse(material);
        if (direct != null) {
            return direct;
        }

        return ResourceLocation.tryParse(material.trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
    }
}
