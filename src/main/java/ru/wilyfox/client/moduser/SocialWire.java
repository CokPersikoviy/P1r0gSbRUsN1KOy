package ru.wilyfox.client.moduser;

import com.google.gson.Gson;
import java.net.URI;
import java.util.List;

/** Versioned wire DTOs, separate from Minecraft state and presentation. */
final class SocialWire {
    static final Gson JSON = new Gson();
    static final URI BACKEND = URI.create("https://cclu.alwaysdata.net");
    static final int MAX_MESSAGE_SIZE = 65_536;

    record Login(String gameToken) {}
    record SessionUpdate(int version, String type, String modVersion, int level, String location) {}
    record Session(String accessToken, long expiresAt, String name) {
        void validate(String expectedName, long nowSeconds) {
            if (accessToken == null || !accessToken.matches("[A-Za-z0-9_-]{43}")
                    || expiresAt <= nowSeconds + 5 || expiresAt > nowSeconds + 601
                    || name == null || !name.equalsIgnoreCase(expectedName)) {
                throw new IllegalArgumentException("Invalid session");
            }
        }
    }
    record Player(String id, String name) {}
    record Snapshot(int version, String type, long sequence, String scope, List<Player> players) {
        List<String> validatedNames(String expectedScope) {
            if (version != 1 || !"presence.snapshot".equals(type) || sequence < 0
                    || !expectedScope.equals(scope) || players == null || players.size() > 256) {
                throw new IllegalArgumentException("Invalid presence snapshot");
            }
            for (Player player : players) {
                if (player == null || player.id() == null || !player.id().matches("[a-f0-9]{64}")
                        || player.name() == null || !player.name().matches("[A-Za-z0-9_]{1,16}")) {
                    throw new IllegalArgumentException("Invalid presence player");
                }
            }
            return players.stream().map(Player::name).toList();
        }
    }
}
