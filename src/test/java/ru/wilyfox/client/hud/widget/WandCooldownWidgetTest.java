package ru.wilyfox.client.hud.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WandCooldownWidgetTest {
    @Test
    void formatsFinalSecondInTenths() {
        assertEquals("1.0s", WandCooldownWidget.formatNumericCooldown(1_000L));
        assertEquals("0.9s", WandCooldownWidget.formatNumericCooldown(899L));
        assertEquals("0.1s", WandCooldownWidget.formatNumericCooldown(1L));
    }

    @Test
    void formatsLongerCooldownsCompactly() {
        assertEquals("2s", WandCooldownWidget.formatNumericCooldown(1_001L));
        assertEquals("1:01", WandCooldownWidget.formatNumericCooldown(60_001L));
        assertEquals("2:05", WandCooldownWidget.formatNumericCooldown(7_500_000L));
    }
}
