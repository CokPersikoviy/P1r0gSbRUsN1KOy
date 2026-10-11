package ru.wilyfox.client.boss;

import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.protocol.DwBossType;
import ru.wilyfox.utils.BossName;

/** Shared boss eligibility for timers and notifications, independent of the visible row limit. */
public final class BossTimerFilter {
    private static final String EXCLUDED = BossName.identityKey("древний страж");
    private BossTimerFilter() {}
    public static boolean accepts(BossInfo boss) {
        return accepts(boss, DiamondWorldProtocolClient.getCurrentLevel());
    }
    public static boolean accepts(BossInfo boss, int playerLevel) {
        return boss != null && !BossBlacklist.isBlocked(boss)
                && !boss.getIdentityKey().contains(EXCLUDED) && acceptsLevel(boss.getLevel(), playerLevel);
    }
    public static boolean accepts(DwBossType boss) {
        return boss != null && !BossBlacklist.isBlocked(boss)
                && !BossName.identityKey(boss.name()).contains(EXCLUDED)
                && acceptsLevel(boss.level(), DiamondWorldProtocolClient.getCurrentLevel());
    }
    private static boolean acceptsLevel(int level, int playerLevel) {
        var config = ConfigManager.get().bossWidget;
        return level >= config.minLevel && level <= config.effectiveMaxLevel(playerLevel);
    }
}
