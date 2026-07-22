package ru.wilyfox.client.clan;

import net.minecraft.network.chat.Component;
import ru.wilyfox.client.chat.ChatMessageSanitizer;
import ru.wilyfox.client.chat.ChatTimestampFormatter;
import ru.wilyfox.utils.Formatting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlayerClanChatParser {
    private static final Pattern TRAILING_LEVEL_PATTERN = Pattern.compile("\\[(\\d{1,3})]$");
    private static final Pattern TRAILING_BRACKET_PATTERN = Pattern.compile("\\[([^\\]]+)]$");
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 520;

    private PlayerClanChatParser() {
    }

    static ParsedClanChatEntry parse(Component component) {
        if (component == null) {
            return null;
        }

        return parse(component.getString());
    }

    /** The sender player name of a DW chat line, or null if it isn't a parseable player message. */
    public static String senderName(Component component) {
        ParsedClanChatEntry entry = parse(component);
        return entry == null ? null : entry.playerName();
    }

    /**
     * Lenient sender extraction: like {@link #senderName} but also handles plain global chat with no
     * clan/level brackets (e.g. "WilyFox: 1111"). Strips timestamps + any [..] brackets from the header
     * before the first colon, then takes the player-name token. Used for mod-user detection, which must
     * see EVERY player message, not just clan/local chat.
     */
    public static String senderNameLenient(Component component) {
        if (component == null) {
            return null;
        }

        String text = Formatting.stripMinecraftFormatting(component.getString()).replace(' ', ' ').trim();
        text = stripAllTimestampPrefixes(ChatMessageSanitizer.forLogic(text));

        int colonIndex = text.indexOf(':');
        if (colonIndex < 0) {
            return null;
        }

        String header = text.substring(0, colonIndex).replaceAll("\\[[^\\]]*]", " ").trim();
        return extractPlayerName(header);
    }

    static ParsedClanChatEntry parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        rawText = ChatMessageSanitizer.forLogic(rawText);

        String text = Formatting.stripMinecraftFormatting(rawText).replace('\u00A0', ' ').trim();
        text = stripAllTimestampPrefixes(text);
        if (text.isBlank()) {
            return null;
        }

        int colonIndex = text.indexOf(':');
        if (colonIndex < 0) {
            return null;
        }

        String header = text.substring(0, colonIndex).trim();
        Matcher levelMatcher = TRAILING_LEVEL_PATTERN.matcher(header);
        if (!levelMatcher.find()) {
            return null;
        }

        int level = Integer.parseInt(levelMatcher.group(1));
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            return null;
        }

        String beforeLevel = header.substring(0, levelMatcher.start()).trim();
        if (beforeLevel.isBlank()) {
            return null;
        }

        String clanName = null;
        Matcher clanMatcher = TRAILING_BRACKET_PATTERN.matcher(beforeLevel);
        if (clanMatcher.find()) {
            clanName = cleanClan(clanMatcher.group(1));
            beforeLevel = beforeLevel.substring(0, clanMatcher.start()).trim();
        }

        String playerName = extractPlayerName(beforeLevel);
        if (playerName == null) {
            return null;
        }

        return new ParsedClanChatEntry(playerName, clanName);
    }

    private static String stripAllTimestampPrefixes(String text) {
        String current = text;

        while (true) {
            String stripped = ChatTimestampFormatter.stripTimestampPrefix(current);
            if (stripped.equals(current)) {
                return current.trim();
            }
            current = stripped.trim();
        }
    }

    private static String cleanClan(String clanName) {
        if (clanName == null) {
            return null;
        }

        String cleaned = clanName.trim().replace('\u00A0', ' ');
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String extractPlayerName(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }

        String[] tokens = header.split("\\s+");
        for (int index = tokens.length - 1; index >= 0; index--) {
            String token = tokens[index].trim();
            if (PLAYER_NAME_PATTERN.matcher(token).matches()) {
                return token;
            }
        }

        return null;
    }
}
