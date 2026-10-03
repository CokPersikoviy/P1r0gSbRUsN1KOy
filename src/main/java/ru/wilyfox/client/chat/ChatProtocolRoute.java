package ru.wilyfox.client.chat;

import net.minecraft.client.Minecraft;

import java.util.regex.Pattern;

/** Extracts the sender only when an explicitly routed private message is addressed to this client. */
public final class ChatProtocolRoute {
    private static final Pattern NON_PLAYER_NAME = Pattern.compile("[^A-Za-z0-9_]");

    private ChatProtocolRoute() {
    }

    public static String extractIncomingSender(String text, String tokenPrefix) {
        if (text == null || tokenPrefix == null) {
            return null;
        }
        int tokenIndex = text.indexOf(tokenPrefix);
        if (tokenIndex <= 0) {
            return null;
        }

        String route = text.substring(0, tokenIndex).trim();
        int pipeIndex = route.indexOf('|');
        if (pipeIndex >= 0) {
            route = route.substring(pipeIndex + 1).trim();
        }
        route = route.replace(':', ' ').trim();

        int arrowIndex = route.indexOf('»');
        if (arrowIndex >= 0) {
            String senderSide = route.substring(0, arrowIndex).trim();
            String recipientSide = route.substring(arrowIndex + 1).trim();
            if (!isLocalRecipient(recipientSide)) {
                return null;
            }
            return lastPlayerName(senderSide);
        }

        // Compatibility with older server formatting that did not include an explicit route arrow.
        return lastPlayerName(route);
    }

    static boolean isLocalRecipient(String value) {
        String recipient = lastPlayerName(value);
        if (recipient == null) {
            return value != null && value.trim().equalsIgnoreCase("Я");
        }

        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null
                && recipient.equalsIgnoreCase(minecraft.player.getGameProfile().name());
    }

    private static String lastPlayerName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split("\\s+");
        for (int index = parts.length - 1; index >= 0; index--) {
            String candidate = NON_PLAYER_NAME.matcher(parts[index]).replaceAll("");
            if (candidate.length() >= 3 && candidate.length() <= 16) {
                return candidate;
            }
        }
        return null;
    }
}
