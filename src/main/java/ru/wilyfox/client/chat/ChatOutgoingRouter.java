package ru.wilyfox.client.chat;

import net.minecraft.client.Minecraft;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import java.util.Locale;

/** Routes only manual chat-screen submissions; automatic game messages keep their own channel. */
public final class ChatOutgoingRouter {
    private ChatOutgoingRouter() {}
    public static boolean isDwConnection() {
        var client = Minecraft.getInstance();
        var server = client.getCurrentServer();
        return client.getConnection() != null && (DiamondWorldProtocolClient.getCurrentServerInfo().isKnown()
                || server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains("diamondworld"));
    }
    public static String replyCommand() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) for (String alias : new String[]{"r", "reply"}) {
            if (connection.getCommands().getRoot().getChild(alias) != null) return "/" + alias + " ";
        }
        return "";
    }
    public static String format(String input) {
        return format(input, ChatDock.outgoingChannel(), isDwConnection(), replyCommand());
    }
    /** Null prevents game delivery: backend channel, unavailable PM reply, or oversized prefixed text. */
    static String format(String input, ChatTab tab, boolean onDw, String replyCommand) {
        if (tab == ChatTab.FH) return null; // Backend chat must never fall through to a game packet.
        String text = input == null ? "" : input.strip();
        if (!onDw || text.isEmpty() || hasExplicitChannel(text)) return text;
        String prefix = switch (tab) {
            case GLOBAL -> "!";
            case TRADE -> "$";
            case CLAN -> "@";
            case PRIVATE -> replyCommand;
            default -> "";
        };
        if (tab == ChatTab.PRIVATE && (prefix == null || prefix.isEmpty())) return null;
        String result = prefix + text;
        return result.length() > 256 ? null : result;
    }
    private static boolean hasExplicitChannel(String text) {
        return "/!$@".indexOf(text.charAt(0)) >= 0;
    }
}
