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
}
