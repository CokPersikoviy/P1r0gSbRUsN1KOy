package ru.wilyfox.client.moduser;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.chat.ChatDispatchQueue;
import ru.wilyfox.client.hud.config.ConfigManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final int MAX_CHAT_LENGTH = 240;
    private static final long SEND_INTERVAL_MS = 3_000L;
    private static final int MAX_NAMES_PER_SYNC = 100;
    private static final char TYPE_PAIR = 'P';
    private static final char TYPE_ACK = 'A';

    private static final Map<String, IncomingBuffer> INCOMING = new HashMap<>();
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
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            INCOMING.clear();
            PAIRED.clear();
            ACKED.clear();
        });
    }

    public static boolean isSocialsEnabled() {
        return ConfigManager.get().render.modUserMesh;
    }

    public static synchronized void setSocialsEnabled(boolean enabled) {
        ConfigManager.get().render.modUserMesh = enabled;
        if (enabled) {
            return;
        }

        PAIRED.clear();
        ACKED.clear();
        ChatDispatchQueue.removeQueuedCommandsContaining(TOKEN_PREFIX);
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

        String sender = extractSender(text);
        String msgId = matcher.group(1);
        int part = Integer.parseInt(matcher.group(2));
        int total = Integer.parseInt(matcher.group(3));
        String payloadPart = matcher.group(4);

        String bufferKey = (sender == null ? "?" : sender.toLowerCase(Locale.ROOT)) + ":" + msgId;
        IncomingBuffer buffer = INCOMING.computeIfAbsent(bufferKey, ignored -> new IncomingBuffer(total, sender));
        buffer.put(part, payloadPart);
        if (!buffer.isComplete()) {
            return true;
        }
        INCOMING.remove(bufferKey);

        String decoded = decode(buffer.join());
        if (decoded == null || decoded.length() < 2 || decoded.charAt(1) != '|') {
            return true;
        }

        char type = decoded.charAt(0);
        List<String> names = parseNames(decoded.substring(2));
        boolean haveSender = buffer.sender != null && !buffer.sender.isBlank() && !isSelf(buffer.sender);
        if (haveSender) {
            ModUserStorage.markKnown(buffer.sender);
            if (isSocialsEnabled()) {
                PAIRED.add(buffer.sender.toLowerCase(Locale.ROOT));
            }
        }
        ModUserStorage.merge(names);

        if (isSocialsEnabled()
                && type == TYPE_PAIR
                && haveSender
                && ACKED.add(buffer.sender.toLowerCase(Locale.ROOT))) {
            send(buffer.sender, TYPE_ACK);
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

    private static List<String> splitPayload(String target, String payload) {
        String msgId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        int overhead = ("m " + target + " " + TOKEN_PREFIX + msgId + ":1:1:" + TOKEN_SUFFIX).length();
        int limit = MAX_CHAT_LENGTH - overhead;
        if (limit <= 0) {
            return List.of();
        }

        int total = Math.max(1, (payload.length() + limit - 1) / limit);
        List<String> chunks = new ArrayList<>(total);
        for (int part = 0; part < total; part++) {
            int from = part * limit;
            int to = Math.min(payload.length(), from + limit);
            chunks.add(TOKEN_PREFIX + msgId + ":" + (part + 1) + ":" + total + ":" + payload.substring(from, to) + TOKEN_SUFFIX);
        }
        return chunks;
    }

    private static List<String> parseNames(String csv) {
        List<String> names = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return names;
        }
        for (String part : csv.split(",")) {
            String name = part.trim();
            if (!name.isBlank()) {
                names.add(name);
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

    private static boolean isSelf(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && name.equalsIgnoreCase(minecraft.player.getGameProfile().getName());
    }

    private static String extractSender(String text) {
        int tokenIndex = text.indexOf(TOKEN_PREFIX);
        if (tokenIndex <= 0) {
            return null;
        }
        String prefix = text.substring(0, tokenIndex).trim();
        int pipeIndex = prefix.indexOf('|');
        if (pipeIndex >= 0) {
            prefix = prefix.substring(pipeIndex + 1).trim();
        }
        prefix = prefix.replace(":", " ");
        String[] parts = prefix.split("\\s+");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].replaceAll("[^A-Za-z0-9_]", "");
            if (candidate.length() >= 3 && candidate.length() <= 16) {
                return candidate;
            }
        }
        return null;
    }

    private static final class IncomingBuffer {
        private final String[] parts;
        private final String sender;

        private IncomingBuffer(int totalParts, String sender) {
            this.parts = new String[Math.max(1, totalParts)];
            this.sender = sender;
        }

        private void put(int partIndex, String payloadPart) {
            if (partIndex >= 1 && partIndex <= parts.length) {
                parts[partIndex - 1] = payloadPart;
            }
        }

        private boolean isComplete() {
            for (String part : parts) {
                if (part == null) {
                    return false;
                }
            }
            return true;
        }

        private String join() {
            return String.join("", parts);
        }
    }
}
