package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import ru.wilyfox.utils.Formatting;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;

public final class BoosterChatDebug {
    private static final Set<String> LOGGED_MESSAGES = new LinkedHashSet<>();

    private BoosterChatDebug() {
    }

    public static void onIncomingMessage(Component component) {
        if (component == null) {
            return;
        }

        String raw = component.getString();
        if (raw == null || raw.isBlank()) {
            return;
        }

        String normalized = Formatting.stripMinecraftFormatting(raw)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isBlank()) {
            return;
        }

        if (normalized.contains(":")) {
            return;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!looksLikeBoosterText(lower)) {
            return;
        }

        if (!LOGGED_MESSAGES.add(lower)) {
            return;
        }

        info(LOGGER, "Booster chat debug: raw='{}', normalized='{}'", raw, normalized);
    }

    private static boolean looksLikeBoosterText(String text) {
        return text.contains("буст")
                || text.contains("бустер")
                || text.contains("глобальный")
                || text.contains("активировал")
                || text.contains("деньг")
                || text.contains("монет")
                || text.contains("шард")
                || text.contains("множител")
                || text.contains("x2")
                || text.contains("x3")
                || text.contains("x4")
                || text.contains("x5")
                || text.contains("х2")
                || text.contains("х3")
                || text.contains("х4")
                || text.contains("х5");
    }
}
