package ru.wilyfox.client.hud.healthbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerHealthBarRendererTest {
    @Test
    void formatsHealthAsClampedWholeValues() {
        assertEquals("20/20", PlayerHealthBarRenderer.formatHealth(19.01F, 20.0F));
        assertEquals("1/20", PlayerHealthBarRenderer.formatHealth(0.01F, 20.0F));
        assertEquals("0/1", PlayerHealthBarRenderer.formatHealth(-5.0F, 0.0F));
    }

    @Test
    void darkensRgbWithoutChangingAlpha() {
        assertEquals(0xA0604020, PlayerHealthBarRenderer.scaleRgb(0xA0C08040, 0.5F));
        assertEquals(0xA0000000, PlayerHealthBarRenderer.scaleRgb(0xA0C08040, -1.0F));
        assertEquals(0xA0C08040, PlayerHealthBarRenderer.scaleRgb(0xA0C08040, 2.0F));
    }
}
