package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BossMessageParserTest {
    @Test
    void parsesStrictCaptureNotification() {
        BossMessageParser.BossCapture capture = BossMessageParser.parseCapture(
                "Босс Вестник ада захвачен кланом Frogs!"
        );

        assertEquals("Вестник ада", capture.bossName());
        assertEquals("Frogs", capture.clanName());
        assertNull(BossMessageParser.parseCapture("Игрок: Босс Вестник ада захвачен кланом Frogs!"));
    }

    @Test
    void parsesBossHealthWithHeartAndDecimalComma() {
        BossMessageParser.BossBarText bossBar = BossMessageParser.parseBossBar("Босс Вестник ада 125,5❤");

        assertEquals("Вестник ада", bossBar.bossName());
        assertEquals(125.5D, bossBar.health());
        assertEquals("", bossBar.label());
    }

    @Test
    void separatesHealthFromReferenceClanSuffixContainingDigits() {
        var parsed = BossMessageParser.parseBossBar("§cКРИГЕР §e125❤ §7(Frogs42)");
        assertEquals("КРИГЕР", parsed.bossName());
        assertEquals(125D, parsed.health());
        assertEquals("Frogs42", parsed.label());
        parsed = BossMessageParser.parseBossBar("LEGION COMMANDER 250❤ (Clan 2)");
        assertEquals("LEGION COMMANDER", parsed.bossName());
        assertEquals(250D, parsed.health());
        assertEquals("Clan 2", parsed.label());
    }

    @Test
    void keepsLegacyHealthAndNonNumericSuffixes() {
        var parsed = BossMessageParser.parseBossBar("Босс Вестник ада 125,5❤ (Frogs)");
        assertEquals("Вестник ада", parsed.bossName());
        assertEquals(125.5D, parsed.health());
        assertEquals("Frogs", parsed.label());
        assertNull(BossMessageParser.parseBossBar("КРИГЕР"));
        assertNull(BossMessageParser.parseBossBar(""));
    }

    @Test
    void excludesClanWaveBossBar() {
        assertNull(BossMessageParser.parseBossBar("Испытание вызова 1:25"));
    }

    @Test
    void preservesStageLabelWithoutMixingItsDigitsWithHealth() {
        var parsed = BossMessageParser.parseBossBar("§bБессмертный Легион §e125,5❤\u00a0(§7 Стадия 2/4 §r)");
        assertEquals("Бессмертный Легион", parsed.bossName());
        assertEquals(125.5D, parsed.health());
        assertEquals("Стадия 2/4", parsed.label());
        assertEquals("", BossMessageParser.parseBossBar("КРИГЕР 125❤ ( )").label());
        assertEquals("", BossMessageParser.parseBossBar("КРИГЕР 125❤ (Стадия 2").label());
    }

    @Test
    void parsesCurseFeature() {
        assertEquals("Слабость", BossMessageParser.parseCurse("Босс проклят! Особенность: Слабость"));
    }
}
