package ru.wilyfox.client.hud.widget;

import org.junit.jupiter.api.Test;
import ru.wilyfox.boss.BossInfo;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BossHudWidgetOrderingTest {
    @Test
    void aRaidAcceleratedPastTheDisplayLimitStillAppears() {
        BossInfo first = new BossInfo("First", 100_000L, 100);
        BossInfo second = new BossInfo("Second", 110_000L, 200);
        BossInfo raid = new BossInfo("Raid", 120_000L, 590);
        var candidates = new ArrayList<>(List.of(first, second, raid));

        BossHudWidget.sortAndLimit(candidates,
                boss -> boss == raid ? Math.round(boss.getRespawnAt() / 1.52D) : boss.getRespawnAt(), 2);

        assertEquals(List.of(raid, first), candidates);
    }

    @Test
    void hidingAllBossRowsProducesAnEmptyList() {
        var candidates = new ArrayList<>(List.of(new BossInfo("Boss", 100_000L, 100)));
        BossHudWidget.sortAndLimit(candidates, BossInfo::getRespawnAt, 0);
        assertEquals(List.of(), candidates);
    }
}
