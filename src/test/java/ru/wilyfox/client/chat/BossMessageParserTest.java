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
    }

    @Test
    void excludesClanWaveBossBar() {
        assertNull(BossMessageParser.parseBossBar("Испытание вызова 1:25"));
    }

    @Test
    void parsesCurseFeature() {
        assertEquals("Слабость", BossMessageParser.parseCurse("Босс проклят! Особенность: Слабость"));
    }
}
