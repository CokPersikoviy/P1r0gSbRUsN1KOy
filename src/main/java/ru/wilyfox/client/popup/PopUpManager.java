package ru.wilyfox.client.popup;

import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.utils.Formatting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

public final class PopUpManager {
    private static final int MAX_BUFFER_SIZE = 32;
    private static final Pattern LEGACY_AMPERSAND_FORMATTING = Pattern.compile(
            "(?i)&x(?:&[0-9a-f]){6}|&[0-9a-fk-or]"
    );
    private static final PopUpManager INSTANCE = new PopUpManager();

    private final Deque<PopUpNotification> notifications = new ArrayDeque<>();

    private PopUpManager() {
    }

    public static PopUpManager getInstance() {
        return INSTANCE;
    }

    public synchronized void publish(PopUpRequest request) {
        if (request == null) {
            return;
        }

        String source = request.source() != null ? request.source() : "generic";
        if (!isSourceEnabled(source)) {
            return;
        }

        int fadeIn = request.fadeInMs() != null ? request.fadeInMs() : ConfigManager.get().popUps.fadeInMillis;
        int hold = request.holdMs() != null ? request.holdMs() : ConfigManager.get().popUps.holdMillis;
        int fadeOut = request.fadeOutMs() != null ? request.fadeOutMs() : ConfigManager.get().popUps.fadeOutMillis;

        notifications.addFirst(new PopUpNotification(
                source,
                sanitizeText(request.title(), "Notification"),
                sanitizeText(request.message(), ""),
                request.severity() != null ? request.severity() : PopUpSeverity.INFO,
                System.currentTimeMillis(),
                Math.max(0, fadeIn),
                Math.max(0, hold),
                Math.max(0, fadeOut)
        ));

        while (notifications.size() > MAX_BUFFER_SIZE) {
            notifications.removeLast();
        }
    }

    public synchronized List<PopUpNotification> getVisibleNotifications(int limit) {
        pruneExpired();

        int max = Math.max(0, limit);
        List<PopUpNotification> visible = new ArrayList<>(Math.min(max, notifications.size()));
        int index = 0;
        for (PopUpNotification notification : notifications) {
            if (index++ >= max) {
                break;
            }
            visible.add(notification);
        }
        return visible;
    }

    public void notifyChatCopied() {
        publish(PopUpRequest.of(
                PopUpSource.CHAT_COPY,
                "Chat copied",
                "Message text copied to clipboard",
                PopUpSeverity.SUCCESS
        ));
    }

    private boolean isSourceEnabled(String source) {
        return switch (source) {
            case PopUpSource.CHAT_COPY -> ConfigManager.get().popUps.chatCopyEvent;
            case PopUpSource.PRIVATE_MESSAGE -> ConfigManager.get().popUps.privateMessageEvent;
            case PopUpSource.BOSS_SPAWN -> ConfigManager.get().popUps.bossSpawnEvent;
            case PopUpSource.BOSS_CAPTURE -> ConfigManager.get().popUps.bossCaptureEvent;
            case PopUpSource.GAME_EVENT -> ConfigManager.get().popUps.gameEvent;
            case PopUpSource.ABILITY_READY -> ConfigManager.get().popUps.abilityReadyEvent;
            case PopUpSource.WAND_READY -> ConfigManager.get().popUps.wandReadyEvent;
            case PopUpSource.SELLER_READY -> ConfigManager.get().popUps.sellerReadyEvent;
            case PopUpSource.MINER_RETURNED -> ConfigManager.get().popUps.minerReturnedEvent;
            case PopUpSource.LEVEL_READY -> ConfigManager.get().popUps.levelReadyEvent;
            case PopUpSource.RUNE_SET_READY -> ConfigManager.get().popUps.runeSetReadyEvent;
            case PopUpSource.POTION_EXPIRED -> ConfigManager.get().popUps.potionExpiredEvent;
            case PopUpSource.BOOSTER_EXPIRED -> ConfigManager.get().popUps.boosterExpiredEvent;
            case PopUpSource.BARREL_FOUND -> ConfigManager.get().popUps.barrelFoundEvent;
            case PopUpSource.GOLDEN_CRYSTAL_FOUND -> ConfigManager.get().popUps.goldenCrystalFoundEvent;
            default -> true;
        };
    }

    private void pruneExpired() {
        long now = System.currentTimeMillis();
        notifications.removeIf(notification -> notification.expiresAtMs() <= now);
    }

    static String sanitizeText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String normalized = LEGACY_AMPERSAND_FORMATTING.matcher(
                        Formatting.stripMinecraftFormatting(value)
                )
                .replaceAll("")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
