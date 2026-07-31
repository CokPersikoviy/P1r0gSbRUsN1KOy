package ru.wilyfox.client.boss;

import org.junit.jupiter.api.Test;
import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.client.protocol.DwBossType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossBlacklistTest {
    @Test
    void matchesExactProtocolId() {
        BossInfo boss = new BossInfo("keeper", "Keeper", 1_000L, 500);

        assertTrue(BossBlacklist.isBlocked(boss, Set.of("keeper")));
        assertFalse(BossBlacklist.isBlocked(boss, Set.of("other")));
    }

    @Test
    void levelFallbackSurvivesProtocolIdReplacement() {
        DwBossType type = new DwBossType("krieger", "Кригер", "STONE", 15, 0, 0, false);

        assertTrue(BossBlacklist.isBlocked(type, Set.of("level:15")));
    }
}
