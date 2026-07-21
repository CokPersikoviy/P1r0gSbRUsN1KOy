package ru.wilyfox.client.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.utils.Formatting;

import java.util.Locale;

public final class AutoThanks {
    private static final long COMMAND_COOLDOWN_MS = 3_000L;

    private static long lastSentAt = 0L;

    private AutoThanks() {
    }

    public static void onIncomingMessage(Component component) {
        if (component == null || !ConfigManager.get().render.autoThanks) {
            return;
        }

        if (!isGlobalBoosterActivation(component)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastSentAt < COMMAND_COOLDOWN_MS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            return;
        }

        ChatDispatchQueue.enqueueCommand("thx", COMMAND_COOLDOWN_MS);
        lastSentAt = now;
    }

    private static boolean isGlobalBoosterActivation(Component component) {
        String normalized = Formatting.stripMinecraftFormatting(component.getString())
                .replace(' ', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);

        if (normalized.isEmpty()) {
            return false;
        }

        // A server booster broadcast has no colon; player chat ("Nick: ...") does. This keeps us
        // from thanking because someone typed the phrase in chat.
        if (normalized.contains(":")) {
            return false;
        }

        // Match tolerantly instead of anchoring on a bare ASCII nickname: real broadcasts carry
        // rank/clan prefixes and Cyrillic that the previous "^[\\w\\s]+ ..." regex never accepted.
        return normalized.contains("активирова") // активирова(л/ла)
                && normalized.contains("глобальн")            // глобальн(ый/ого)
                && normalized.contains("буст");                                   // буст(ер)
    }
}
