package ru.wilyfox.client.chat;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.boss.BossRepository;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class BossShareService {
    private static final String COMMAND = "/fhshare";
    private static final String TOKEN_PREFIX = "{fhb:";
    private static final String TOKEN_SUFFIX = "}";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{fhb:([A-Za-z0-9_-]+):([1-9]\\d*):([1-9]\\d*):([A-Za-z0-9_-]+)}");
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_CHAT_LENGTH = 240;
    private static final long SEND_INTERVAL_MS = 1_000L;
    private static final int MAX_INCOMING_PARTS = 32;
    private static final int MAX_ENCODED_PAYLOAD_LENGTH = 8_192;
    private static final int MAX_DECOMPRESSED_PAYLOAD_LENGTH = 65_536;
    private static final int MAX_SHARED_BOSSES = 512;
    private static final long INCOMING_TTL_MS = 30_000L;

    private static BossRepository repository;
    private static final MultipartChatMessageAssembler incomingShares = new MultipartChatMessageAssembler(
            64,
            MAX_INCOMING_PARTS,
            MAX_CHAT_LENGTH,
            MAX_ENCODED_PAYLOAD_LENGTH,
            INCOMING_TTL_MS
    );
    private static boolean initialized = false;

    private BossShareService() {
    }

    public static synchronized int diagnosticPendingShareCount() {
        return incomingShares.size();
    }

    public static void bindRepository(BossRepository bossRepository) {
        repository = bossRepository;
        register();
    }

    private static void register() {
        if (initialized) {
            return;
        }

        initialized = true;

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            incomingShares.clear();
        });
    }

    public static boolean handleOutgoingCommand(String rawInput, boolean addToHistory) {
        if (rawInput == null) {
            return false;
        }

        String normalized = rawInput.trim();
        if (!normalized.toLowerCase(Locale.ROOT).startsWith(COMMAND)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            return true;
        }

        if (addToHistory && minecraft.gui != null) {
            minecraft.gui.hud.getChat().addRecentChat(normalized);
        }

        String targetName = normalized.length() > COMMAND.length()
                ? normalized.substring(COMMAND.length()).trim()
                : "";

        if (targetName.isBlank()) {
            showLocalMessage("Использование: /fhshare <ник>");
            return true;
        }

        if (repository == null) {
            showLocalMessage("Таймеры боссов недоступны.");
            return true;
        }

        String payload = encodeBosses(repository.getAllMerged());
        if (payload == null || payload.isBlank()) {
            showLocalMessage("Нет боссов для отправки.");
            return true;
        }

        List<String> chunks = splitPayload(targetName, payload);
        if (chunks.isEmpty()) {
            showLocalMessage("Не удалось подготовить таймеры для отправки.");
            return true;
        }

        for (String chunk : chunks) {
            ChatDispatchQueue.enqueueCommand("m " + targetName + " " + chunk, SEND_INTERVAL_MS);
        }

        showLocalMessage("Таймеры боссов отправлены игроку " + targetName + " (" + chunks.size() + " ч.).");
        return true;
    }

    public static boolean handleIncomingShare(Component component) {
        if (component == null || repository == null) {
            return false;
        }

        String text = component.getString();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        if (!matcher.find()) {
            return false;
        }

        String sender = ChatProtocolRoute.extractIncomingSender(text, TOKEN_PREFIX);
        String shareId = matcher.group(1);
        int partIndex = parsePositiveInt(matcher.group(2));
        int totalParts = parsePositiveInt(matcher.group(3));
        String payloadPart = matcher.group(4);
        if (partIndex < 1 || totalParts < 1) {
            return true;
        }

        MultipartChatMessageAssembler.Result assembled = incomingShares.accept(
                sender,
                shareId,
                partIndex,
                totalParts,
                payloadPart,
                System.currentTimeMillis()
        );
        if (assembled.status() != MultipartChatMessageAssembler.Status.COMPLETE) {
            return true;
        }

        Map<Integer, Long> sharedTimers = decodeBosses(assembled.payload());
        if (sharedTimers.isEmpty()) {
            showLocalMessage("Не удалось импортировать таймеры боссов.");
            return true;
        }

        long now = Instant.now().toEpochMilli();
        for (Map.Entry<Integer, Long> entry : sharedTimers.entrySet()) {
            int level = entry.getKey();
            long remainingMillis = entry.getValue();
            String bossName = resolveBossName(level);
            repository.upsert(bossName, now + remainingMillis);
        }

        if (sender == null || sender.isBlank()) {
            showLocalMessage("Импортировано таймеров боссов: " + sharedTimers.size() + ".");
        } else {
            showLocalMessage("Импортировано таймеров боссов от " + sender + ": " + sharedTimers.size() + ".");
        }

        return true;
    }

    private static List<String> splitPayload(String targetName, String payload) {
        String shareId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        int overhead = ("m " + targetName + " " + TOKEN_PREFIX + shareId + ":1:1:" + TOKEN_SUFFIX).length();
        int partPayloadLimit = MAX_CHAT_LENGTH - overhead;
        if (partPayloadLimit <= 0) {
            return List.of();
        }

        int totalParts = (payload.length() + partPayloadLimit - 1) / partPayloadLimit;
        List<String> chunks = new ArrayList<>(totalParts);

        for (int part = 0; part < totalParts; part++) {
            int from = part * partPayloadLimit;
            int to = Math.min(payload.length(), from + partPayloadLimit);
            String piece = payload.substring(from, to);
            chunks.add(TOKEN_PREFIX + shareId + ":" + (part + 1) + ":" + totalParts + ":" + piece + TOKEN_SUFFIX);
        }

        return chunks;
    }

    private static String encodeBosses(Iterable<BossInfo> bosses) {
        long now = Instant.now().toEpochMilli();
        List<BossInfo> encodable = new ArrayList<>();

        for (BossInfo boss : bosses) {
            long remainingMillis = boss.getRespawnAt() - now;
            if (boss.getLevel() <= 0 || remainingMillis <= 0L) {
                continue;
            }

            encodable.add(boss);
        }

        if (encodable.isEmpty()) {
            return null;
        }

        encodable.sort(Comparator.comparingInt(BossInfo::getLevel));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeByte(FORMAT_VERSION);
            buf.writeVarInt(encodable.size());

            for (BossInfo boss : encodable) {
                long remainingSeconds = Math.max(0L, (boss.getRespawnAt() - now + 999L) / 1000L);
                buf.writeVarInt(boss.getLevel());
                buf.writeVarInt((int) Math.min(Integer.MAX_VALUE, remainingSeconds));
            }

            byte[] raw = new byte[buf.readableBytes()];
            buf.getBytes(0, raw);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(raw));
        } finally {
            buf.release();
        }
    }

    static Map<Integer, Long> decodeBosses(String payload) {
        try {
            byte[] compressed = Base64.getUrlDecoder().decode(payload);
            byte[] raw = inflate(compressed);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(raw));

            try {
                int version = buf.readUnsignedByte();
                if (version != FORMAT_VERSION) {
                    return Map.of();
                }

                int count = buf.readVarInt();
                if (count < 0 || count > MAX_SHARED_BOSSES) {
                    return Map.of();
                }
                Map<Integer, Long> decoded = new LinkedHashMap<>();

                for (int i = 0; i < count; i++) {
                    int level = buf.readVarInt();
                    long remainingMillis = buf.readVarInt() * 1000L;
                    if (level > 0 && remainingMillis > 0L) {
                        decoded.put(level, remainingMillis);
                    }
                }

                return decoded;
            } finally {
                buf.release();
            }
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();

        ByteArrayOutputStream output = new ByteArrayOutputStream(raw.length);
        byte[] buffer = new byte[256];
        while (!deflater.finished()) {
            int written = deflater.deflate(buffer);
            output.write(buffer, 0, written);
        }
        deflater.end();
        return output.toByteArray();
    }

    private static byte[] inflate(byte[] compressed) throws Exception {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);

            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(compressed.length * 2, 4_096));
            byte[] buffer = new byte[256];
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read == 0) {
                    if (inflater.needsInput()) {
                        throw new IllegalArgumentException("Boss share compressed stream is truncated");
                    }
                    throw new IllegalArgumentException("Boss share inflater made no progress");
                }
                if (output.size() + read > MAX_DECOMPRESSED_PAYLOAD_LENGTH) {
                    throw new IllegalArgumentException("Boss share payload exceeds decompressed size limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            inflater.end();
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

    private static String resolveBossName(int level) {
        String protocolName = DiamondWorldProtocolClient.getBossNameByLevel(level);
        if (protocolName != null && !protocolName.isBlank()) {
            return protocolName;
        }

        String repositoryName = repository != null ? repository.findBossNameByLevel(level) : null;
        if (repositoryName != null && !repositoryName.isBlank()) {
            return repositoryName;
        }

        return "Boss [" + level + "]";
    }

    private static void showLocalMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) {
            minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
        }
    }

}
