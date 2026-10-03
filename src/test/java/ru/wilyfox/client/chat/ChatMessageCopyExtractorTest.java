package ru.wilyfox.client.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.moduser.ModUserBadge;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChatMessageCopyExtractorTest {
    @Test
    void copiesWholeParentEvenWhenOlderWrappedLinesWereTrimmed() {
        String text = "Fox: first line\nsecond line with  two spaces";
        GuiMessage parent = new GuiMessage(0, Component.literal("§a" + text), null, GuiMessageSource.SYSTEM_SERVER, null);
        FormattedCharSequence fragment = FormattedCharSequence.forward("two spaces", Style.EMPTY);
        List<GuiMessage.Line> visible = List.of(new GuiMessage.Line(parent, fragment, true));

        assertEquals(text, ChatMessageCopyExtractor.messageText(visible, fragment));
        assertEquals("", ChatMessageCopyExtractor.messageText(visible, null));
    }

    @Test
    void identicalWrappedTextSelectsItsOwnParent() {
        FormattedCharSequence first = FormattedCharSequence.forward("same", Style.EMPTY);
        FormattedCharSequence second = FormattedCharSequence.forward("same", Style.EMPTY);
        GuiMessage firstParent = new GuiMessage(0, Component.literal("first same"), null, GuiMessageSource.SYSTEM_SERVER, null);
        GuiMessage secondParent = new GuiMessage(1, Component.literal("second same"), null, GuiMessageSource.SYSTEM_SERVER, null);
        List<GuiMessage.Line> visible = List.of(
                new GuiMessage.Line(secondParent, second, true), new GuiMessage.Line(firstParent, first, true));

        assertEquals("first same", ChatMessageCopyExtractor.messageText(visible, first));
    }

    @Test
    void renderedPoseControlsHoverAndSpacingGapsDoNotSelectText() {
        FormattedCharSequence line = FormattedCharSequence.forward("message", Style.EMPTY);
        ActiveTextCollector.Parameters parameters = new ActiveTextCollector.Parameters(
                new Matrix3x2f().translate(4, 100).scale(0.5f));
        ChatMessageCopyExtractor.HoveredLineFinder hit = new ChatMessageCopyExtractor.HoveredLineFinder(104);
        hit.accept(TextAlignment.LEFT, 0, 8, parameters, line);
        assertSame(line, hit.result);

        ChatMessageCopyExtractor.HoveredLineFinder gap = new ChatMessageCopyExtractor.HoveredLineFinder(109);
        gap.accept(TextAlignment.LEFT, 0, 8, parameters, line);
        assertNull(gap.result);
    }

    @Test
    void fullMessageCopyPreservesDisplayedTextExceptFrogMarkers() {
        String message = "[12:34:56] [Clan] [VIP] Fox: hello\u00A0world";
        String displayed = ModUserBadge.prefix(Component.literal(message)).getString();

        assertEquals(message, ChatMessageCopyExtractor.selectCopiedText(
                ChatMessageSanitizer.forLogic(displayed),
                true
        ));
    }

    @Test
    void normalCopyStillExtractsOnlyMessageBody() {
        String displayed = "[12:34:56] Fox: hello world";

        assertEquals("hello world", ChatMessageCopyExtractor.selectCopiedText(displayed, false));
        assertEquals("first line\nsecond line", ChatMessageCopyExtractor.selectCopiedText("Fox: first line\nsecond line", false));
    }
}
