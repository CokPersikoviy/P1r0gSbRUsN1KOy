package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.utils.Formatting;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutoThanks {
    private static final Pattern ENGLISH_BOOSTER = Pattern.compile("activated the global .*booster");
    private static final Pattern RUSSIAN_BOOSTER = Pattern.compile("активирова(?:л|ла) глобальн(?:ый|ого) бустер");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static boolean initialized;

    private AutoThanks() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        // Listen to the server message, before HUD timestamps and tab filtering, as EvoPlus does.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onIncomingMessage(message));
    }

    public static void onIncomingMessage(Component component) {
        if (component == null || !ConfigManager.get().render.autoThanks) {
            return;
        }

        if (!isGlobalBoosterActivation(component.getString())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            return;
        }

        // EvoPlus sends /thx for every activation without an additional local cooldown.
        // Queue it for the next tick so server-requested delays and retries still apply.
        ChatDispatchQueue.enqueuePriorityCommand("thx", 0L);
    }

    static boolean isGlobalBoosterActivation(String text) {
        if (text == null) {
            return false;
        }
        String normalized = WHITESPACE.matcher(ChatTimestampFormatter.stripTimestampPrefix(
                        Formatting.stripMinecraftFormatting(text)).replace('\u00A0', ' '))
                .replaceAll(" ")
                .trim()
                .toLowerCase(Locale.ROOT);

        if (normalized.isEmpty()) {
            return false;
        }

        // A colon before the activation phrase is a player-chat delimiter. A colon in the
        // booster description is valid, and local HH:mm:ss timestamps were stripped above.
        return matchesBroadcast(ENGLISH_BOOSTER, normalized) || matchesBroadcast(RUSSIAN_BOOSTER, normalized);
    }

    private static boolean matchesBroadcast(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() && !text.substring(0, matcher.start()).contains(":");
    }
}
