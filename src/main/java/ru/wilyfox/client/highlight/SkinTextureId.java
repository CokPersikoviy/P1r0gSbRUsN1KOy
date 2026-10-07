package ru.wilyfox.client.highlight;

import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Skin identity excludes the profile name, timestamp, JSON formatting and URL scheme. */
final class SkinTextureId {
    private static final Map<String, String> CACHE = new LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> entry) {
            return size() > 128;
        }
    };

    private SkinTextureId() {}

    static String fromValue(String value) {
        if (value == null || value.isBlank() || value.length() > 16_384) return "";
        return CACHE.computeIfAbsent(value, SkinTextureId::decode);
    }

    private static String decode(String value) {
        try {
            var json = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8));
            String url = json.getAsJsonObject().getAsJsonObject("textures")
                    .getAsJsonObject("SKIN").get("url").getAsString();
            URI uri = URI.create(url);
            if (!"textures.minecraft.net".equalsIgnoreCase(uri.getHost())) return "";
            String path = uri.getPath();
            return path != null && path.matches("/texture/[0-9a-fA-F]{16,64}")
                    ? path.substring("/texture/".length()).toLowerCase(java.util.Locale.ROOT) : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
