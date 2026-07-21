package ru.wilyfox.client.moduser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.ability.AbilityCooldownStore;
import ru.wilyfox.client.chat.ChatDispatchQueue;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Silent peer-to-peer mesh discovery of FrogHelper users over DiamondWorld private messages. When a mod
 * user is spotted in chat (via the invisible marker), we PM them a pairing token carrying our known list;
 * they record us, merge our list, and reply with theirs. Names learned this way are just recorded (never
 * re-PM'd), so the knowledge gossips across the player base without a message storm. Protocol PMs are
 * hidden from chat. Guards against flooding: throttled send queue, pair once per session, P->A handshake
 * only (no reply loops), self-exclusion, a capped payload, and a config toggle.
 */
public final class ModUserProtocol {
    private static final String TOKEN_PREFIX = "{fhmu:";
    private static final String TOKEN_SUFFIX = "}";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\\{fhmu:([A-Za-z0-9_-]+):([1-9]\\d*):([1-9]\\d*):([A-Za-z0-9_-]+)}");
    private static final Pattern KEEPALIVE_PATTERN = Pattern.compile("\\{fhka:([A-Za-z0-9_-]+)}");
    private static final Pattern GROUP_PATTERN = Pattern.compile("\\{fhg:([A-Za-z0-9_-]+)}");
    private static final Pattern COOLDOWN_PATTERN = Pattern.compile("\\{fhcd:([A-Za-z0-9_-]+)}");
    private static final long GROUP_SEND_INTERVAL_MS = 700L;
    // DW's "player not found" reply to a PM (e.g. a groupmate on another subserver / offline).
    private static final Pattern NOT_FOUND_PATTERN =
            Pattern.compile("Игрок\\s+\"?([A-Za-z0-9_]{3,16})\"?\\s+не найден");
    private static final int MAX_CHAT_LENGTH = 240;
    private static final long SEND_INTERVAL_MS = 3_000L; // conservative — avoid PM anti-flood mutes
    private static final int MAX_NAMES_PER_SYNC = 100;   // bound the payload / chunk count
    private static final char TYPE_PAIR = 'P';
    private static final char TYPE_ACK = 'A';

    private static final long KEEPALIVE_FAST_MS = 10_000L;        // group member heard from recently -> every 10s
    private static final long KEEPALIVE_PROBE_MS = 30_000L;       // silent member -> slow probe (maybe offline)
    private static final long KEEPALIVE_SEND_INTERVAL_MS = 700L;  // min gap between the queued keepalive PMs
    private static final long STATUS_TTL_MS = 25_000L;            // status stale after 25s (2.5x fast interval)

    private static final Map<String, IncomingBuffer> INCOMING = new HashMap<>();
    private static final Set<String> PAIRED = new HashSet<>(); // sent (or received) a pairing this session
    private static final Set<String> ACKED = new HashSet<>();  // already replied with an ack this session
    private static final Map<String, Status> STATUS = new ConcurrentHashMap<>();   // live keepalive status
    private static final Map<String, Long> NEXT_KEEPALIVE_AT = new HashMap<>();    // per-member send schedule
    private static final Map<String, Map<String, Long>> COOLDOWNS = new ConcurrentHashMap<>(); // player -> ability -> endsAt
    private static final Map<String, Long> LAST_CD_BROADCAST = new HashMap<>();    // local abilityId -> last broadcast endsAt

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
            STATUS.clear();
            NEXT_KEEPALIVE_AT.clear();
            COOLDOWNS.clear();
            LAST_CD_BROADCAST.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(ModUserProtocol::tick);
    }

    private static boolean enabled() {
        return ConfigManager.get().render.modUserMesh;
    }

    /**
     * PM a compact keepalive (name + level + HP, one message, no chunks) to each GROUP member on an adaptive
     * schedule: every 10s to members we've heard from recently, a slow 30s probe otherwise (so an offline
     * member isn't spammed but is re-detected when they return). The tab list is unreliable on DW (many
     * subservers), so "online" here means a fresh keepalive received, not tab-list presence.
     */
    private static void tick(Minecraft client) {
        if (!enabled() || client.player == null || client.getConnection() == null) {
            return;
        }
        List<String> group = SocialGroup.keepaliveTargets();
        if (group.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        String token = null; // built once, lazily, only if at least one member is due
        for (String member : group) {
            if (member == null || member.isBlank() || isSelf(member)) {
                continue;
            }
            String key = member.toLowerCase(Locale.ROOT);
            if (now < NEXT_KEEPALIVE_AT.getOrDefault(key, 0L)) {
                continue;
            }
            boolean online = getStatus(member) != null; // fresh keepalive within the TTL window
            NEXT_KEEPALIVE_AT.put(key, now + (online ? KEEPALIVE_FAST_MS : KEEPALIVE_PROBE_MS));
            if (token == null) {
                token = buildKeepaliveToken(client);
            }
            ChatDispatchQueue.enqueueCommand("m " + member + " " + token, KEEPALIVE_SEND_INTERVAL_MS);
        }

        pollAndBroadcastCooldowns(group, now);
    }

    /** Broadcast any newly-started local ability cooldown to the group (once per cooldown window). */
    private static void pollAndBroadcastCooldowns(List<String> group, long now) {
        for (AbilityCooldownStore.Entry entry : DiamondWorldProtocolClient.getAbilityCooldowns()) {
            if (entry.endsAt() <= now) {
                continue;
            }
            Long prev = LAST_CD_BROADCAST.get(entry.id());
            if (prev != null && entry.endsAt() <= prev + 500L) {
                continue; // same cooldown window — already broadcast
            }
            LAST_CD_BROADCAST.put(entry.id(), entry.endsAt());
            String ability = entry.name().replace("|", "/");
            String token = "{fhcd:" + encode(myName() + "|" + ability + "|" + entry.endsAt()) + "}";
            for (String member : group) {
                ChatDispatchQueue.enqueueCommand("m " + member + " " + token, GROUP_SEND_INTERVAL_MS);
            }
        }
    }

    private static void handleCooldown(String encoded) {
        String decoded = decode(encoded);
        if (decoded == null) {
            return;
        }
        String[] parts = decoded.split("\\|", -1);
        if (parts.length < 3) {
            return;
        }
        String sender = parts[0];
        if (sender == null || sender.isBlank() || isSelf(sender)) {
            return; // our own echo
        }
        String ability = parts[1];
        if (ability == null || ability.isBlank()) {
            return;
        }
        long endsAt;
        try {
            endsAt = Long.parseLong(parts[2]);
        } catch (NumberFormatException ignored) {
            return;
        }
        Map<String, Long> map = COOLDOWNS.computeIfAbsent(sender.toLowerCase(Locale.ROOT), key -> new ConcurrentHashMap<>());
        if (endsAt <= System.currentTimeMillis()) {
            map.remove(ability);
        } else {
            map.put(ability, endsAt);
        }
    }

    /** Active ability cooldowns of a known player (shortest remaining first), for the Group widget. */
    public static List<CooldownView> getCooldowns(String name) {
        if (name == null) {
            return List.of();
        }
        Map<String, Long> map = COOLDOWNS.get(name.toLowerCase(Locale.ROOT));
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(entry -> entry.getValue() <= now);
        List<CooldownView> out = new ArrayList<>();
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            out.add(new CooldownView(entry.getKey(), entry.getValue() - now));
        }
        out.sort((a, b) -> Long.compare(a.remainingMs(), b.remainingMs()));
        return out;
    }

    public record CooldownView(String ability, long remainingMs) {
        public int seconds() {
            return (int) Math.ceil(Math.max(0L, remainingMs) / 1000.0);
        }
    }

    private static String buildKeepaliveToken(Minecraft client) {
        int level = DiamondWorldProtocolClient.getCurrentLevel();
        int hp = Math.max(0, Math.round(client.player.getHealth()));
        int maxHp = Math.max(0, Math.round(client.player.getMaxHealth()));
        // Name is embedded in the payload (not parsed from the PM prefix) so the receiver keys status
        // reliably and drops our own echoed PM (name == self) regardless of DW's PM echo behaviour.
        String me = client.player.getGameProfile().getName();
        return "{fhka:" + encode("K|" + me + "|" + level + "|" + hp + "|" + maxHp) + "}";
    }

    /** Live status of a known mod user, from their keepalive PMs. Null from {@link #getStatus} once stale. */
    public record Status(int level, int hp, int maxHp, long updatedAt) {
    }

    public static Status getStatus(String name) {
        if (name == null) {
            return null;
        }
        Status status = STATUS.get(name.toLowerCase(Locale.ROOT));
        if (status == null || System.currentTimeMillis() - status.updatedAt() > STATUS_TTL_MS) {
            return null;
        }
        return status;
    }

    /** A mod user was seen in chat: pair with them once per session (send them our known list). */
    public static synchronized void onModUserSeen(String name) {
        if (!enabled() || name == null || name.isBlank() || isSelf(name)) {
            return;
        }
        if (!PAIRED.add(name.toLowerCase(Locale.ROOT))) {
            return; // already paired this session
        }
        send(name, TYPE_PAIR);
    }

    /** Handle an incoming PM: if it carries a mesh token, merge + (for a pair) reply, and hide it. */
    public static synchronized boolean handleIncoming(Component component) {
        if (component == null) {
            return false;
        }
        String text = component.getString();

        // Hide DW's "player not found" reply for a groupmate we're actively keepalive/sync-PMing (they're
        // just offline or on another subserver). Only roster names — a manual /msg to a stranger still shows.
        Matcher notFound = NOT_FOUND_PATTERN.matcher(text);
        if (notFound.find() && SocialGroup.inRoster(notFound.group(1))) {
            return true;
        }

        Matcher keepalive = KEEPALIVE_PATTERN.matcher(text);
        if (keepalive.find()) {
            handleKeepalive(keepalive.group(1));
            return true; // hide the keepalive PM
        }

        Matcher group = GROUP_PATTERN.matcher(text);
        if (group.find()) {
            handleGroup(group.group(1));
            return true; // hide the group-control PM
        }

        Matcher cooldown = COOLDOWN_PATTERN.matcher(text);
        if (cooldown.find()) {
            handleCooldown(cooldown.group(1));
            return true; // hide the ability-cooldown PM
        }

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
            return true; // malformed, but still hide it
        }
        char type = decoded.charAt(0);
        List<String> names = parseNames(decoded.substring(2));

        boolean haveSender = buffer.sender != null && !buffer.sender.isBlank() && !isSelf(buffer.sender);
        if (haveSender) {
            // The PM sender definitely runs the mod (they sent the protocol) — record + don't also P them.
            ModUserStorage.markKnown(buffer.sender);
            PAIRED.add(buffer.sender.toLowerCase(Locale.ROOT));
        }
        ModUserStorage.merge(names);

        // A pairing request gets one reply (our list). An ack triggers nothing → no loops.
        if (type == TYPE_PAIR && haveSender && ACKED.add(buffer.sender.toLowerCase(Locale.ROOT))) {
            send(buffer.sender, TYPE_ACK);
        }
        return true;
    }

    private static void handleKeepalive(String encoded) {
        String decoded = decode(encoded);
        if (decoded == null || !decoded.startsWith("K|")) {
            return;
        }
        String[] parts = decoded.substring(2).split("\\|"); // sender|level|hp|maxHp
        if (parts.length < 4) {
            return;
        }
        String sender = parts[0];
        if (sender == null || sender.isBlank() || isSelf(sender)) {
            return; // our own echoed keepalive, or malformed
        }
        try {
            int level = Integer.parseInt(parts[1]);
            int hp = Integer.parseInt(parts[2]);
            int maxHp = Integer.parseInt(parts[3]);
            ModUserStorage.markKnown(sender); // a keepalive proves they run the mod (and are online)
            STATUS.put(sender.toLowerCase(Locale.ROOT), new Status(level, hp, maxHp, System.currentTimeMillis()));
        } catch (NumberFormatException ignored) {
        }
    }

    // ---- Group sync protocol ({fhg:...}) -------------------------------------------------------------
    // Packet = base64 of "TYPE|<senderName>|<rosterCsv?>". TYPE: I=invite A=accept R=refuse U=roster-update
    // L=leave K=kick D=disband. Sender name embedded (reliable keying + self-echo drop). One msg, no chunks.

    private static void handleGroup(String encoded) {
        String decoded = decode(encoded);
        if (decoded == null) {
            return;
        }
        String[] parts = decoded.split("\\|", -1);
        if (parts.length < 2) {
            return;
        }
        String type = parts[0];
        String sender = parts[1];
        if (sender == null || sender.isBlank() || isSelf(sender)) {
            return; // ignore our own echoed packet
        }
        ModUserStorage.markKnown(sender); // a group packet proves they run the mod
        String rosterCsv = parts.length > 2 ? parts[2] : "";
        switch (type) {
            case "I" -> onInvite(sender, rosterCsv);
            case "A" -> onAccept(sender);
            case "R" -> onRefuse(sender);
            case "U" -> onRosterUpdate(sender, rosterCsv);
            case "L" -> onLeave(sender);
            case "K" -> onKick(sender);
            case "D" -> onDisband(sender);
            default -> {
            }
        }
    }

    private static void onInvite(String leader, String rosterCsv) {
        if (SocialGroup.isInGroup()) {
            sendGroup(leader, "R|" + myName()); // "прости, брат, я в группе"
            return;
        }
        SocialGroup.joinAsMember(leader, parseNames(rosterCsv));
        sendGroup(leader, "A|" + myName());
        showLocalMessage("Ты вступил в группу " + leader + ".");
    }

    private static void onAccept(String member) {
        if (!SocialGroup.isLeader() || (SocialGroup.isFull() && !SocialGroup.inRoster(member))) {
            return;
        }
        SocialGroup.addToRoster(member);
        broadcastRoster();
        showLocalMessage(member + " вступил в группу.");
    }

    private static void onRefuse(String member) {
        if (SocialGroup.isLeader()) {
            showLocalMessage(member + " уже состоит в другой группе — не добавлен.");
        }
    }

    private static void onRosterUpdate(String leader, String rosterCsv) {
        if (SocialGroup.isMember() && leader.equalsIgnoreCase(SocialGroup.leader())) {
            SocialGroup.setRoster(parseNames(rosterCsv));
        }
    }

    private static void onLeave(String member) {
        if (SocialGroup.isLeader() && SocialGroup.inRoster(member)) {
            SocialGroup.removeFromRoster(member);
            broadcastRoster();
            showLocalMessage(member + " вышел из группы.");
        }
    }

    private static void onKick(String leader) {
        if (SocialGroup.isMember() && leader.equalsIgnoreCase(SocialGroup.leader())) {
            SocialGroup.clear();
            showLocalMessage("Тебя удалили из группы " + leader + ".");
        }
    }

    private static void onDisband(String leader) {
        if (SocialGroup.isMember() && leader.equalsIgnoreCase(SocialGroup.leader())) {
            SocialGroup.clear();
            showLocalMessage("Группа " + leader + " распущена.");
        }
    }

    // ---- Group actions (called from the Social screen) -----------------------------------------------

    /** Leader-only: invite a player (become the leader if not in a group yet). */
    public static void invite(String target) {
        if (target == null || target.isBlank() || SocialGroup.isSelf(target)) {
            return;
        }
        if (SocialGroup.isMember()) {
            showLocalMessage("Ты уже в группе " + SocialGroup.leader() + " — сначала выйди.");
            return;
        }
        if (!SocialGroup.isLeader()) {
            SocialGroup.becomeLeader();
        }
        if (SocialGroup.inRoster(target)) {
            return;
        }
        if (SocialGroup.isFull()) {
            showLocalMessage("Группа заполнена (макс " + SocialGroup.MAX + ").");
            return;
        }
        sendGroup(target, "I|" + myName() + "|" + String.join(",", SocialGroup.roster()));
        showLocalMessage("Приглашение отправлено: " + target + ".");
    }

    /** Leader-only: remove a member. */
    public static void kick(String target) {
        if (!SocialGroup.isLeader() || SocialGroup.isSelf(target) || !SocialGroup.inRoster(target)) {
            return;
        }
        sendGroup(target, "K|" + myName());
        SocialGroup.removeFromRoster(target);
        broadcastRoster();
        showLocalMessage(target + " удалён из группы.");
    }

    /** Leave the group — leader disbands it, member just leaves. */
    public static void leaveGroup() {
        if (SocialGroup.isLeader()) {
            for (String member : SocialGroup.keepaliveTargets()) {
                sendGroup(member, "D|" + myName());
            }
            SocialGroup.clear();
            showLocalMessage("Группа распущена.");
        } else if (SocialGroup.isMember()) {
            sendGroup(SocialGroup.leader(), "L|" + myName());
            SocialGroup.clear();
            showLocalMessage("Ты вышел из группы.");
        }
    }

    private static void broadcastRoster() {
        if (!SocialGroup.isLeader()) {
            return;
        }
        String roster = String.join(",", SocialGroup.roster());
        for (String member : SocialGroup.keepaliveTargets()) {
            sendGroup(member, "U|" + myName() + "|" + roster);
        }
    }

    private static void sendGroup(String target, String payload) {
        if (target == null || target.isBlank()) {
            return;
        }
        ChatDispatchQueue.enqueueCommand("m " + target + " {fhg:" + encode(payload) + "}", GROUP_SEND_INTERVAL_MS);
    }

    private static String myName() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? "" : minecraft.player.getGameProfile().getName();
    }

    private static void showLocalMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) {
            minecraft.gui.getChat().addMessage(Component.literal("[FrogHelper] " + message));
        }
    }

    private static void send(String target, char type) {
        if (!enabled()) {
            return;
        }
        StringBuilder names = new StringBuilder();
        int count = 0;
        for (String name : ModUserStorage.knownDisplayNames()) {
            if (name == null || name.equalsIgnoreCase(target)) {
                continue; // no need to tell them about themselves
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
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String part : csv.split(",")) {
            String name = part.trim();
            if (!name.isBlank()) {
                out.add(name);
            }
        }
        return out;
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
        int tokenIndex = text.indexOf("{fh"); // works for both {fhmu:...} and {fhka:...}
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
            StringBuilder builder = new StringBuilder();
            for (String part : parts) {
                builder.append(part);
            }
            return builder.toString();
        }
    }
}
