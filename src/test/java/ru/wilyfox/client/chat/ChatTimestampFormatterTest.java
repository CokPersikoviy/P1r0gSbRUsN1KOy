package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.config.ChatTimestampConfig;
import java.time.LocalDateTime;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;

class ChatTimestampFormatterTest {
    @Test void everyFormatCanBeRemovedForRoutingAndCopying() {
        var instant = LocalDateTime.of(2026, 10, 11, 13, 4, 5).atZone(ZoneId.systemDefault()).toInstant();
        var settings = new ChatTimestampConfig();
        for (var format : ChatTimestampConfig.Format.values()) for (var brackets : ChatTimestampConfig.Brackets.values()) {
            settings.format = format; settings.brackets = brackets;
            String prefix = ChatTimestampFormatter.createTimestampPrefix(instant, settings).getString();
            String time = switch (format) {
                case TIME_SECONDS -> "13:04:05"; case TIME -> "13:04";
                case TIME_12_SECONDS -> "01:04:05 PM"; case TIME_12 -> "01:04 PM";
            };
            assertEquals(brackets.opening + time + brackets.closing + " ", prefix);
            assertEquals("G Fox: hello", ChatTimestampFormatter.stripTimestampPrefix(prefix + "G Fox: hello"));
            assertEquals(ChatTab.GLOBAL, ChatPrefixRouter.resolve(prefix + "G Fox: hello"));
            assertEquals("hello", ChatMessageCopyExtractor.selectCopiedText(prefix + "G Fox: hello", false));
        }
    }

    @Test void colorAndEmphasisDoNotAffectTheMessageBody() {
        var settings = new ChatTimestampConfig(); settings.themeColor = false; settings.color = 0xFF3399;
        settings.bold = true; settings.italic = true;
        var prefix = ChatTimestampFormatter.createTimestampPrefix(java.time.Instant.now(), settings);
        assertEquals(0xFF3399, prefix.getStyle().getColor().getValue());
        assertTrue(prefix.getStyle().isBold()); assertTrue(prefix.getStyle().isItalic());
        assertEquals("[VIP] Fox: hello", ChatTimestampFormatter.stripTimestampPrefix("[VIP] Fox: hello"));
    }
}
