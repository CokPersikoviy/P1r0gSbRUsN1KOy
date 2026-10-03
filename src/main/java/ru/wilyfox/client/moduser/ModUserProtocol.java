package ru.wilyfox.client.moduser;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.chat.ChatProtocolRoute;
import ru.wilyfox.client.chat.ChatDispatchQueue;
import ru.wilyfox.client.chat.MultipartChatMessageAssembler;
import ru.wilyfox.client.hud.config.ConfigManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Silent peer-to-peer discovery of FrogHelper users over DiamondWorld private messages.
 */
public final class ModUserProtocol {
    private static final String TOKEN_PREFIX = "{fhmu:";
    private static final String TOKEN_SUFFIX = "}";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\\{fhmu:([A-Za-z0-9_-]+):([1-9]\\d*):([1-9]\\d*):([A-Za-z0-9_-]+)}");
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final int MAX_CHAT_LENGTH = 240;
    private static final long SEND_INTERVAL_MS = 3_000L;
    private static final int MAX_NAMES_PER_SYNC = 100;
    private static final int MAX_INCOMING_PARTS = 32;
    private static final int MAX_INCOMING_PAYLOAD_LENGTH = 8_192;
    private static final long INCOMING_TTL_MS = 30_000L;
    private static final char TYPE_PAIR = 'P';
    private static final char TYPE_ACK = 'A';

    private static final MultipartChatMessageAssembler INCOMING = new MultipartChatMessageAssembler(
            64,
            MAX_INCOMING_PARTS,
            MAX_CHAT_LENGTH,
            MAX_INCOMING_PAYLOAD_LENGTH,
            INCOMING_TTL_MS
    );
    private static final Set<String> PAIRED = new HashSet<>();
    private static final Set<String> ACKED = new HashSet<>();

    private static boolean initialized;

    private ModUserProtocol() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearSession());
    }

    public static boolean isSocialsEnabled() {
        return ConfigManager.get().render.modUserMesh;
    }

    public static synchronized void setSocialsEnabled(boolean enabled) {
        ConfigManager.get().render.modUserMesh = enabled;
        if (enabled) {
            return;
        }

        clearSession();
        ChatDispatchQueue.removeQueuedCommandsContaining(TOKEN_PREFIX);
    }

    private static synchronized void clearSession() {
        INCOMING.clear();
        PAIRED.clear();
        ACKED.clear();
    }

    public static synchronized DebugSnapshot diagnosticSnapshot() {
        return new DebugSnapshot(INCOMING.size(), PAIRED.size(), ACKED.size());
    }

    public static synchronized void onModUserSeen(String name) {
        if (!isSocialsEnabled() || name == null || name.isBlank() || isSelf(name)) {
            return;
        }
        if (!PAIRED.add(name.toLowerCase(Locale.ROOT))) {
            return;
        }
        send(name, TYPE_PAIR);
    }

    public static synchronized boolean handleIncoming(Component component) {
        if (component == null) {
            return false;
        }

        String text = component.getString();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        if (!matcher.find()) {
            return false;
        }
        if (!isSocialsEnabled()) {
            return true;
        }

        String sender = ChatProtocolRoute.extractIncomingSender(text, TOKEN_PREFIX);
        String msgId = matcher.group(1);
        int part = parsePositiveInt(matcher.group(2));
        int total = parsePositiveInt(matcher.group(3));
        String payloadPart = matcher.group(4);
        if (part < 1 || total < 1) {
            return true;
        }

        MultipartChatMessageAssembler.Result assembled = INCOMING.accept(
                sender,
                msgId,
                part,
                total,
                payloadPart,
                System.currentTimeMillis()
        );
        if (assembled.status() != MultipartChatMessageAssembler.Status.COMPLETE) {
            return true;
        }

        String decoded = decode(assembled.payload());
        if (decoded == null || decoded.length() < 2 || decoded.charAt(1) != '|') {
            return true;
        }

        char type = decoded.charAt(0);
        if (type != TYPE_PAIR && type != TYPE_ACK) {
            return true;
        }
        List<String> names = parseNames(decoded.substring(2));
        boolean haveSender = sender != null && !sender.isBlank() && !isSelf(sender);
        if (haveSender) {
            ModUserStorage.markKnown(sender);
            if (isSocialsEnabled()) {
                PAIRED.add(sender.toLowerCase(Locale.ROOT));
            }
        }
        ModUserStorage.merge(names);

        if (isSocialsEnabled()
                && type == TYPE_PAIR
                && haveSender
                && ACKED.add(sender.toLowerCase(Locale.ROOT))) {
            send(sender, TYPE_ACK);
        }
        return true;
    }

    private static void send(String target, char type) {
        if (!isSocialsEnabled()) {
            return;
        }

        StringBuilder names = new StringBuilder();
        int count = 0;
        for (String name : ModUserStorage.knownDisplayNames()) {
            if (name == null || name.equalsIgnoreCase(target)) {
                continue;
            }
            if (count >= MAX_NAMES_PER_SYNC) {
                break;
            }
            if (count > 0) {
                names.append(',');
            }
            names.append(name);
            count++;
        }

        String payload = encode(type + "|" + names);
        for (String chunk : splitPayload(target, payload)) {
            ChatDispatchQueue.enqueueCommand("m " + target + " " + chunk, SEND_INTERVAL_MS);
        }
    }

    static List<String> splitPayload(String target, String payload) {
        String msgId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        int fixedOverhead = ("m " + target + " " + TOKEN_PREFIX + msgId + ":::" + TOKEN_SUFFIX).length();
        int total = 1;
        int limit;
        while (true) {
            // Both counters grow to two digits for larger syncs. Account for their actual width
            // before splitting, otherwise the final private message exceeds the server limit.
            limit = MAX_CHAT_LENGTH - fixedOverhead - 2 * Integer.toString(total).length();
            if (limit <= 0) {
                return List.of();
            }
            int requiredParts = Math.max(1, (payload.length() + limit - 1) / limit);
            if (requiredParts > MAX_INCOMING_PARTS) {
                return List.of();
            }
            if (requiredParts == total) {
                break;
            }
            total = requiredParts;
        }
        List<String> chunks = new ArrayList<>(total);
        for (int part = 0; part < total; part++) {
            int from = part * limit;
            int to = Math.min(payload.length(), from + limit);
            chunks.add(TOKEN_PREFIX + msgId + ":" + (part + 1) + ":" + total + ":" + payload.substring(from, to) + TOKEN_SUFFIX);
        }
        return chunks;
    }

    static List<String> parseNames(String csv) {
        List<String> names = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return names;
        }
        for (String part : csv.split(",")) {
            String name = part.trim();
            if (PLAYER_NAME_PATTERN.matcher(name).matches()) {
                names.add(name);
                if (names.size() >= MAX_NAMES_PER_SYNC) {
                    break;
                }
            }
        }
        return names;
    }

    private static String encode(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String encoded) {
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean isSelf(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && name.equalsIgnoreCase(minecraft.player.getGameProfile().name());
    }

    public record DebugSnapshot(int incomingBuffers, int pairedPlayers, int acknowledgedPlayers) {
    }
}
