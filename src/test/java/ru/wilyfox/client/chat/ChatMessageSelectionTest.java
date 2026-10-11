package ru.wilyfox.client.chat;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChatMessageSelectionTest {
    private static GuiMessage message(String body) {
        return new GuiMessage(0, Component.literal("[12:34:56] G Fox: " + body), null, GuiMessageSource.SYSTEM_SERVER, null);
    }

    @Test void dragCopiesWholeMessagesChronologicallyWithRealNewlines() {
        var older = message("first\nwrapped second line"); var middle = message("second"); var newer = message("third");
        var selection = new ChatMessageSelection();
        selection.press("global", List.of(newer, newer, middle, older, older), newer, false, false);
        assertTrue(selection.drag("global", older));
        selection.release();
        assertEquals("first\nwrapped second line\nsecond\nthird", selection.copiedText(false));
        assertEquals("[12:34:56] G Fox: first\nwrapped second line\n[12:34:56] G Fox: second\n[12:34:56] G Fox: third", selection.copiedText(true));
        assertFalse(selection.isDragging());
    }

    @Test void controlTogglesSeparateMessagesAndShiftAddsTheRange() {
        var first = message("first"); var second = message("second"); var third = message("third");
        var messages = List.of(third, second, first); var selection = new ChatMessageSelection();
        selection.press("tab", messages, first, false, false);
        selection.press("tab", messages, third, true, false);
        assertEquals("first\nthird", selection.copiedText(false));
        selection.press("tab", messages, first, true, false);
        assertEquals("third", selection.copiedText(false));
        selection.press("tab", messages, third, false, true);
        assertEquals("first\nsecond\nthird", selection.copiedText(false));
    }

    @Test void identicalMessagesRemainDistinctAndChangingWindowClearsSelection() {
        var first = message("same"); var second = message("same");
        assertEquals(first, second); // Equal records still represent two actual incoming messages.
        var selection = new ChatMessageSelection();
        selection.press("global", List.of(second, first), first, false, false);
        selection.drag("global", second);
        assertEquals("same\nsame", selection.copiedText(false));
        assertFalse(selection.drag("clan", first));
        selection.press("clan", List.of(second), second, true, true);
        assertEquals("same", selection.copiedText(false));
        selection.clear(); assertFalse(selection.hasSelection()); assertEquals("", selection.copiedText(false));
    }

    @Test void trimmingTheAnchorDoesNotSelectUnrelatedMessages() {
        var first = message("first"); var second = message("second"); var third = message("third");
        var selection = new ChatMessageSelection();
        selection.press("tab", List.of(second, first), first, false, false);
        selection.press("tab", List.of(third, second), third, false, true);
        assertEquals("third", selection.copiedText(false));
    }
}
