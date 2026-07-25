package ru.wilyfox.client.popup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PopUpManagerTest {
    @Test
    void stripsMinecraftFormattingFromDisplayedText() {
        assertEquals(
                "Посох силы is ready",
                PopUpManager.sanitizeText("\u00A7eПосох силы \u00A7ris ready", "")
        );
        assertEquals(
                "Посох силы is ready",
                PopUpManager.sanitizeText("&eПосох силы &ris ready", "")
        );
    }

    @Test
    void normalizesWhitespaceButPreservesMessagePunctuation() {
        assertEquals(
                "Potion (95%) expired: take another!",
                PopUpManager.sanitizeText(" Potion\u00A0(95%)\nexpired: take another! ", "")
        );
    }

    @Test
    void usesFallbackWhenFormattingWasTheOnlyContent() {
        assertEquals("Notification", PopUpManager.sanitizeText("\u00A7e\u00A7l", "Notification"));
    }
}
