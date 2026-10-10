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
    record Player(String id, String name, String scope, Integer timerCount, boolean protocolOnly) {
        Player(String id, String name) { this(id, name, null, null, false); }
        boolean protocolBadge() { return protocolOnly && timerCount != null && timerCount > 50; }
    }
    record Snapshot(int version, String type, long sequence, String scope, List<Player> players, boolean timerSharing, boolean chatSharing) {
        Snapshot(int version, String type, long sequence, String scope, List<Player> players, boolean timerSharing) { this(version,type,sequence,scope,players,timerSharing,false); }
        Snapshot(int version, String type, long sequence, String scope, List<Player> players) { this(version, type, sequence, scope, players, false); }
        List<String> validatedNames(String expectedScope) {
            if (version != 1 || !"presence.snapshot".equals(type) || sequence < 0
                    || !expectedScope.equals(scope) || players == null || players.size() > 256) {
                throw new IllegalArgumentException("Invalid presence snapshot");
            }
            for (Player player : players) {
                if (player == null || player.id() == null || !player.id().matches("[a-f0-9]{64}")
                        || player.name() == null || !player.name().matches("[A-Za-z0-9_]{1,16}")
                        || player.scope() != null && !validScope(player.scope())
                        || player.timerCount() != null && (!validScope(player.scope()) || player.timerCount() < 0 || player.timerCount() > 512)) {
                    throw new IllegalArgumentException("Invalid presence player");
                }
            }
            return players.stream().map(Player::name).toList();
        }
    }
    static boolean validScope(String scope) { return scope != null && scope.matches("[A-Z]{1,24}[0-9]{0,8}:[0-9]{1,8}"); }
    /** HTTP Date comes from the authenticated backend, independent of the player's wall clock. */
    static List<Timer> receivedTimers(TimerResponse payload, Player player, java.net.http.HttpHeaders headers,
                                      long receivedAt, long localNow) {
        if (payload == null) throw new IllegalArgumentException("Missing timer response");
        if (!player.name().equalsIgnoreCase(payload.name())) throw new IllegalArgumentException("Wrong timer author");
        long serverNow = Math.addExact(responseTime(headers, receivedAt), Math.max(0, localNow - receivedAt));
        long clockOffset = Math.subtractExact(localNow, serverNow);
        return payload.validatedTimers(player.id(), player.scope(), serverNow).stream()
                .map(timer -> new Timer(timer.name(), timer.level(), Math.addExact(timer.respawnAt(), clockOffset))).toList();
    }
    static long responseTime(java.net.http.HttpHeaders headers, long fallback) {
        long serverTime = fallback;
        var date = headers.firstValue("Date");
        if (date.isPresent()) {
            try {
                serverTime = java.time.ZonedDateTime.parse(date.get(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli();
            } catch (java.time.DateTimeException invalid) {
                throw new IllegalArgumentException("Invalid backend Date header");
            }
        }
        return serverTime;
    }
    record Timer(String name, int level, long respawnAt) {
        void validate(long now) {
            if (name == null || name.isBlank() || !name.equals(name.trim()) || name.codePointCount(0, name.length()) > 64
                    || name.codePoints().anyMatch(Character::isISOControl) || level < 0 || level > 1_000_000
                    || respawnAt < now - 60_000 || respawnAt > now + 604_800_000)
                throw new IllegalArgumentException("Invalid timer");
        }
    }
    record TimerUpload(int version, String scope, boolean protocolOnly, List<Timer> timers) {}
    record TimerResponse(int version, String id, String name, String scope, long updatedAt, List<Timer> timers) {
        List<Timer> validatedTimers(String expectedId, String expectedScope, long now) {
            if (version != 1) throw new IllegalArgumentException("Unsupported timer response version");
            if (!expectedId.equals(id) || name == null || !name.matches("[A-Za-z0-9_]{1,16}"))
                throw new IllegalArgumentException("Invalid timer author");
            if (!expectedScope.equals(scope)) throw new IllegalArgumentException("Timer source changed subserver");
            if (updatedAt < now - 45_000) throw new IllegalArgumentException("Timer snapshot expired");
            if (updatedAt > now + 5_000) throw new IllegalArgumentException("Timer snapshot ahead of backend clock");
            if (timers == null || timers.size() > 512) throw new IllegalArgumentException("Invalid timer count");
            var names = new java.util.HashSet<String>();
            for (Timer timer : timers) {
                if (timer == null) throw new IllegalArgumentException("Invalid timer");
                timer.validate(now);
                if (!names.add(timer.name().toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("Duplicate timer");
            }
            return timers.stream().filter(timer -> timer.respawnAt() > now).toList();
        }
    }
}
