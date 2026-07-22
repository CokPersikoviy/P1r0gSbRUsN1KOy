package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatDispatchQueue {
    private static final Pattern CHAT_BLOCK_PATTERN = Pattern.compile("Подождите\\s+(\\d+)с\\s+до\\s+отправки\\s+сообщения", Pattern.CASE_INSENSITIVE);
    private static final long REQUEUE_GRACE_WINDOW_MS = 1_500L;

    private static final Deque<QueuedMessage> QUEUE = new ArrayDeque<>();
    private static boolean initialized = false;
    private static long lastSentAt = 0L;
    private static long blockedUntilMs = 0L;
    private static QueuedMessage lastAttemptedMessage = null;
    private static long lastAttemptedAt = 0L;

    private ChatDispatchQueue() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            QUEUE.clear();
            lastSentAt = 0L;
            blockedUntilMs = 0L;
            lastAttemptedMessage = null;
            lastAttemptedAt = 0L;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("tick/ChatDispatchQueue")) {
                if (client.player == null || client.player.connection == null || QUEUE.isEmpty()) {
                    return;
                }

                long now = System.currentTimeMillis();
                if (now < blockedUntilMs) {
                    return;
                }

                QueuedMessage next = QUEUE.peek();
                if (next == null || now - lastSentAt < next.delayMs()) {
                    return;
                }

                QUEUE.poll();
                lastAttemptedMessage = next;
                lastAttemptedAt = now;
                if (next.type() == MessageType.CHAT) {
                    client.player.connection.sendChat(next.content());
                } else {
                    client.player.connection.sendCommand(next.content());
                }
                lastSentAt = now;
            }
        });
    }

    public static void enqueueChat(String message, long delayMs) {
        enqueue(MessageType.CHAT, message, delayMs);
    }

    public static void enqueueCommand(String command, long delayMs) {
        enqueue(MessageType.COMMAND, command, delayMs);
    }

    public static void removeQueuedCommandsContaining(String fragment) {
        if (fragment == null || fragment.isEmpty()) {
            return;
        }

        QUEUE.removeIf(message -> message.type() == MessageType.COMMAND && message.content().contains(fragment));
        if (lastAttemptedMessage != null
                && lastAttemptedMessage.type() == MessageType.COMMAND
                && lastAttemptedMessage.content().contains(fragment)) {
            lastAttemptedMessage = null;
            lastAttemptedAt = 0L;
        }
    }

    public static void handleIncomingMessage(Component component) {
        if (component == null) {
            return;
        }

        String text = Formatting.stripMinecraftFormatting(component.getString()).replace('\u00A0', ' ').trim();
        Matcher matcher = CHAT_BLOCK_PATTERN.matcher(text);
        if (!matcher.find()) {
            return;
        }

        long now = System.currentTimeMillis();
        int seconds = parseSeconds(matcher.group(1));
        if (seconds <= 0) {
            return;
        }

        blockedUntilMs = Math.max(blockedUntilMs, now + seconds * 1000L);

        if (lastAttemptedMessage != null && now - lastAttemptedAt <= REQUEUE_GRACE_WINDOW_MS) {
            QUEUE.addFirst(lastAttemptedMessage);
            lastAttemptedMessage = null;
            lastAttemptedAt = 0L;
        }
    }

    public static DebugSnapshot getDebugSnapshot() {
        QueuedMessage next = QUEUE.peek();
        return new DebugSnapshot(
                QUEUE.size(),
                next != null ? formatPreview(next) : "Queue is empty",
                Math.max(0L, blockedUntilMs - System.currentTimeMillis())
        );
    }

    public static boolean containsQueuedChat(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = message.trim();
        for (QueuedMessage queued : QUEUE) {
            if (queued.type() == MessageType.CHAT && queued.content().equals(normalized)) {
                return true;
            }
        }

        return lastAttemptedMessage != null
                && System.currentTimeMillis() - lastAttemptedAt <= REQUEUE_GRACE_WINDOW_MS
                && lastAttemptedMessage.type() == MessageType.CHAT
                && lastAttemptedMessage.content().equals(normalized);
    }

    private static void enqueue(MessageType type, String content, long delayMs) {
        if (content == null || content.isBlank()) {
            return;
        }

        QUEUE.add(new QueuedMessage(type, content, Math.max(0L, delayMs)));
    }

    private enum MessageType {
        CHAT,
        COMMAND
    }

    private static String formatPreview(QueuedMessage message) {
        String prefix = message.type() == MessageType.COMMAND ? "/" : "";
        return prefix + message.content();
    }

    private static int parseSeconds(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public record DebugSnapshot(int size, String preview, long blockedRemainingMs) {
    }

    private record QueuedMessage(MessageType type, String content, long delayMs) {
    }
}
