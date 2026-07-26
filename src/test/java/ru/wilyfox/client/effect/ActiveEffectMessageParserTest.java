package ru.wilyfox.client.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveEffectMessageParserTest {
    @Test
    void parsesRegenerationDebuffAfterChatSanitization() {
        ActiveEffectMessageParser.ParsedEffect effect = ActiveEffectMessageParser.parse(
                "§cВам отключили\u00A0регенерацию на §e7 §cсек."
        ).orElseThrow();

        assertEquals("regeneration_disabled", effect.id());
        assertEquals("Regeneration Disabled", effect.displayName());
        assertEquals(ActiveEffectKind.DEBUFF, effect.kind());
        assertEquals(7_000L, effect.durationMillis());
    }

    @Test
    void parsesMagicPowerDebuff() {
        ActiveEffectMessageParser.ParsedEffect effect = ActiveEffectMessageParser.parse(
                "Ваша магическая сила снижена на 10 сек."
        ).orElseThrow();

        assertEquals("magic_power_reduced", effect.id());
        assertEquals(ActiveEffectKind.DEBUFF, effect.kind());
        assertEquals(10_000L, effect.durationMillis());
    }

    @Test
    void ignoresUnrelatedChat() {
        assertTrue(ActiveEffectMessageParser.parse("Ваша магическая сила восстановлена.").isEmpty());
    }
}
