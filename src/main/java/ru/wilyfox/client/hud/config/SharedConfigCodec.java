package ru.wilyfox.client.hud.config;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Settings only: no chat/clan/timer stores, account identity, tokens or options.txt. */
public final class SharedConfigCodec {
    public static final int MAX_BYTES = 131_072;
    private static final Gson GSON = HudConfigCodec.createGson();
    public static final Set<String> BINDINGS = Set.of("Clicker", "Editing Mode", "Quick Access", "Settings Menu",
            "Clan Hide", "Clan Siege Map", "Runes Bag", "Social");
    private SharedConfigCodec() {}

    public static JsonObject pack(JsonElement settings, JsonObject bindings) {
        var result = new JsonObject(); result.addProperty("version", 1);
        result.add("settings", settings.deepCopy()); result.add("keybinds", bindings.deepCopy());
        return normalize(result);
    }

    public static JsonObject normalize(JsonElement input) {
        if (input == null || !input.isJsonObject()) throw new IllegalArgumentException("Invalid config");
        checkTree(input, 0, new int[]{0});
        var object = input.getAsJsonObject();
        if (!object.has("version") || object.get("version").getAsInt() != 1
                || !object.has("settings") || !object.get("settings").isJsonObject())
            throw new IllegalArgumentException("Unsupported config");
        var settings = GSON.toJsonTree(HudConfigCodec.decode(GSON, object.get("settings"))).getAsJsonObject();
        var keys = new JsonObject();
        if (object.has("keybinds") && object.get("keybinds").isJsonObject()) {
            for (var entry : object.getAsJsonObject("keybinds").entrySet()) {
                if (!BINDINGS.contains(entry.getKey()) || !entry.getValue().isJsonPrimitive()
                        || !entry.getValue().getAsJsonPrimitive().isString()) continue;
                String key = entry.getValue().getAsString();
                if (!key.matches("key\\.(?:keyboard|mouse)\\.[a-z0-9_.]{1,40}"))
                    throw new IllegalArgumentException("Invalid binding");
                keys.addProperty(entry.getKey(), key);
            }
        }
        var result = new JsonObject(); result.addProperty("version", 1);
        result.add("settings", settings); result.add("keybinds", keys);
        if (encode(result).getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Config is too large");
        return result;
    }

    public static JsonObject parse(String text) {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Config is too large");
        return normalize(JsonParser.parseString(text));
    }
    public static String encode(JsonObject value) { return new Gson().toJson(value); }
    private static void checkTree(JsonElement value, int depth, int[] nodes) {
        if (depth > 24 || ++nodes[0] > 15_000) throw new IllegalArgumentException("Config is too complex");
        if (value.isJsonObject()) {
            if (value.getAsJsonObject().size() > 512) throw new IllegalArgumentException("Too many settings");
            for (var entry : value.getAsJsonObject().entrySet()) {
                if (entry.getKey().length() > 128) throw new IllegalArgumentException("Invalid setting name");
                checkTree(entry.getValue(), depth + 1, nodes);
            }
        } else if (value.isJsonArray()) {
            if (value.getAsJsonArray().size() > 512) throw new IllegalArgumentException("Too many settings");
            for (var child : value.getAsJsonArray()) checkTree(child, depth + 1, nodes);
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && value.getAsString().length() > 8192) throw new IllegalArgumentException("Setting text is too long");
    }
}
