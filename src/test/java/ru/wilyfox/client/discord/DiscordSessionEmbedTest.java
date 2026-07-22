package ru.wilyfox.client.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordSessionEmbedTest {
    private static final UUID PLAYER_ID = UUID.fromString("12345678-1234-5678-9abc-def012345678");
    private static final Instant JOINED_AT = Instant.ofEpochSecond(1_700_000_000L);

    @Test
    void onlineEmbedContainsPlayerLocationAndOnlineField() {
        JsonObject embed = embed(new JoinWebhookNotifier.SessionSnapshot(
                "WilyFox", PLAYER_ID, JOINED_AT, "Spawn", null, 0L
        ));

        assertEquals("WilyFox", fieldValue(embed, "Nickname"));
        assertEquals("<t:1700000000:F>", fieldValue(embed, "Timestamp"));
        assertEquals("Spawn", fieldValue(embed, "Location"));
        assertEquals("Yes", fieldValue(embed, "Online"));
        assertFalse(hasField(embed, "Logout timestamp"));
        assertEquals(
                "https://mc-heads.net/avatar/12345678123456789abcdef012345678/128",
                embed.getAsJsonObject("thumbnail").get("url").getAsString()
        );
    }

    @Test
    void logoutEmbedReplacesOnlineFieldWithLogoutTimestamp() {
        Instant loggedOutAt = Instant.ofEpochSecond(1_700_000_900L);
        JsonObject embed = embed(new JoinWebhookNotifier.SessionSnapshot(
                "WilyFox", PLAYER_ID, JOINED_AT, "Mine 12", loggedOutAt, 2L
        ));

        assertFalse(hasField(embed, "Online"));
        assertEquals("<t:1700000900:F>", fieldValue(embed, "Logout timestamp"));
        assertEquals(loggedOutAt.toString(), embed.get("timestamp").getAsString());
    }

    private static JsonObject embed(JoinWebhookNotifier.SessionSnapshot snapshot) {
        return DiscordSessionEmbed.build(snapshot)
                .getAsJsonArray("embeds")
                .get(0)
                .getAsJsonObject();
    }

    private static boolean hasField(JsonObject embed, String name) {
        return fields(embed).asList().stream()
                .map(element -> element.getAsJsonObject().get("name").getAsString())
                .anyMatch(name::equals);
    }

    private static String fieldValue(JsonObject embed, String name) {
        return fields(embed).asList().stream()
                .map(element -> element.getAsJsonObject())
                .filter(field -> name.equals(field.get("name").getAsString()))
                .findFirst()
                .orElseThrow()
                .get("value")
                .getAsString();
    }

    private static JsonArray fields(JsonObject embed) {
        JsonArray fields = embed.getAsJsonArray("fields");
        assertTrue(fields.size() >= 4);
        return fields;
    }
}
