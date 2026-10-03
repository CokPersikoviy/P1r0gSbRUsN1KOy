package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.clan.PlayerClanChatParser;
import ru.wilyfox.client.moduser.ModUserBadge;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatMessageSanitizerTest {
    @Test
    void removesDisplayBadgeButPreservesOrdinaryUserText() {
        Component decorated = ModUserBadge.prefix(Component.literal("C Fox: helloⒻ"));

        assertEquals("C Fox: helloⒻ", ChatMessageSanitizer.forLogic(decorated).getString());
    }

    @Test
    void frogBadgeDoesNotAffectChatRouting() {
        String decorated = ModUserBadge.prefix(Component.literal("C Fox: hello")).getString();

        assertEquals(ChatTab.CLAN, ChatPrefixRouter.resolve(decorated));
    }

    @Test
    void frogBadgeDoesNotAffectSenderParsing() {
        Component decorated = ModUserBadge.prefix(Component.literal("C [VIP] WilyFox: hello"));

        assertEquals("WilyFox", PlayerClanChatParser.senderNameLenient(decorated));
    }
}
