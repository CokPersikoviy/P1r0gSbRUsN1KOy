package ru.wilyfox.client.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.UUID;

final class DiscordSessionEmbed {
    private static final int ONLINE_COLOR = 0x43B581;
    private static final int OFFLINE_COLOR = 0xF04747;

    private DiscordSessionEmbed() {
    }

    static JsonObject build(JoinWebhookNotifier.SessionSnapshot session) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Player session");
        embed.addProperty("color", session.loggedOutAt() == null ? ONLINE_COLOR : OFFLINE_COLOR);
        embed.addProperty("timestamp", eventTimestamp(session).toString());

        JsonObject thumbnail = new JsonObject();
        thumbnail.addProperty("url", avatarUrl(session.playerId(), session.playerName()));
        embed.add("thumbnail", thumbnail);

        JsonArray fields = new JsonArray();
        fields.add(field("Nickname", session.playerName(), true));
        fields.add(field("Timestamp", discordTimestamp(session.joinedAt()), true));
        fields.add(field("Location", session.locationName(), false));
        if (session.loggedOutAt() == null) {
            fields.add(field("Online", "Yes", true));
        } else {
            fields.add(field("Logout timestamp", discordTimestamp(session.loggedOutAt()), true));
        }
        embed.add("fields", fields);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);

        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new JsonArray());

        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);
        payload.add("allowed_mentions", allowedMentions);
        return payload;
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value != null && !value.isBlank() ? value : "Unknown");
        field.addProperty("inline", inline);
        return field;
    }

    private static Instant eventTimestamp(JoinWebhookNotifier.SessionSnapshot session) {
        return session.loggedOutAt() != null ? session.loggedOutAt() : session.joinedAt();
    }

    private static String discordTimestamp(Instant timestamp) {
        return "<t:" + timestamp.getEpochSecond() + ":F>";
    }

    static String avatarUrl(UUID playerId, String playerName) {
        String player = playerId != null
                ? playerId.toString().replace("-", "")
                : playerName;
        return "https://mc-heads.net/avatar/" + player + "/128";
    }
}
