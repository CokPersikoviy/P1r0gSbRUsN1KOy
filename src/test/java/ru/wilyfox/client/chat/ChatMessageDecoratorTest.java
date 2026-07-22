package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.moduser.ModUserBadge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatMessageDecoratorTest {
    @Test
    void insertsBadgeAfterSymbolPrefix() {
        String original = "Ⓖ [VIP] WilyFox: hello";
        String decorated = ChatMessageDecorator.insertModUserBadge(Component.literal(original)).getString();

        assertFalse(decorated.startsWith(badgeGlyph()));
        assertEquals("Ⓖ " + badgeGlyph() + " [VIP] WilyFox: hello", decorated);
        assertEquals(original, ModUserBadge.strip(decorated));
    }

    @Test
    void insertsBadgeAfterBracketedPrefix() {
        String original = "[Клан] [VIP] WilyFox: hello";
        String decorated = ChatMessageDecorator.insertModUserBadge(Component.literal(original)).getString();

        assertEquals("[Клан] " + badgeGlyph() + " [VIP] WilyFox: hello", decorated);
        assertEquals(original, ModUserBadge.strip(decorated));
    }

    @Test
    void insertsBadgeAfterEveryPrimaryChatPrefix() {
        for (String prefix : new String[]{"Ⓖ", "Ⓜ", "Ⓛ", "C", "PM", "ЛС"}) {
            String original = prefix + " WilyFox: hello";
            String decorated = ChatMessageDecorator.insertModUserBadge(Component.literal(original)).getString();

            assertEquals(prefix + " " + badgeGlyph() + " WilyFox: hello", decorated);
            assertEquals(original, ModUserBadge.strip(decorated));
        }
    }

    private static String badgeGlyph() {
        return ModUserBadge.prefix(Component.empty()).getString().strip();
    }
}
