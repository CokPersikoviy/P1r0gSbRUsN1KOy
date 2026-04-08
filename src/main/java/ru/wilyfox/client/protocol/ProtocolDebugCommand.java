package ru.wilyfox.client.protocol;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public final class ProtocolDebugCommand {
    private static final String COMMAND = "/fhproto";

    private ProtocolDebugCommand() {
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
        if (addToHistory && minecraft.gui != null) {
            minecraft.gui.getChat().addRecentChat(normalized);
        }

        String args = normalized.length() > COMMAND.length()
                ? normalized.substring(COMMAND.length()).trim().toLowerCase(Locale.ROOT)
                : "stats";

        switch (args) {
            case "", "stats" -> showLines(DiamondWorldProtocolClient.getDiagnosticsStats());
            case "anomalies" -> showLines(DiamondWorldProtocolClient.getDiagnosticsAnomalies());
            case "reset" -> {
                DiamondWorldProtocolClient.resetDiagnostics();
                showLocalMessage("Protocol diagnostics reset.");
            }
            default -> showLocalMessage("Usage: /fhproto <stats|anomalies|reset>");
        }

        return true;
    }

    private static void showLines(List<String> lines) {
        for (String line : lines) {
            showLocalMessage(line);
        }
    }

    private static void showLocalMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) {
            minecraft.gui.getChat().addMessage(Component.literal("[FH Protocol] " + message));
        }
    }
}
