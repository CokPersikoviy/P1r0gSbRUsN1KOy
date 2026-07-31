package ru.wilyfox.client.boss;

import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.protocol.BossTypeCatalog;
import ru.wilyfox.client.protocol.DwBossType;

import java.util.LinkedHashSet;
import java.util.Set;

public final class BossBlacklist {
    private BossBlacklist() {
    }

    public static boolean isBlocked(BossInfo boss) {
        return isBlocked(boss, ConfigManager.get().bossWidget.blacklist);
    }

    static boolean isBlocked(BossInfo boss, Set<String> blacklist) {
        if (boss == null || blacklist == null || blacklist.isEmpty()) {
            return false;
        }

        if (contains(blacklist, boss.getId())) {
            return true;
        }
        if (boss.getLevel() > 0 && contains(blacklist, BossTypeCatalog.fallbackId(boss.getLevel()))) {
            return true;
        }

        return isBlocked(BossTypeCatalog.resolve(boss), blacklist);
    }

    public static boolean isBlocked(DwBossType type) {
        return isBlocked(type, ConfigManager.get().bossWidget.blacklist);
    }

    static boolean isBlocked(DwBossType type, Set<String> blacklist) {
        if (type == null || blacklist == null || blacklist.isEmpty()) {
            return false;
        }
        return contains(blacklist, type.id())
                || type.level() > 0 && contains(blacklist, BossTypeCatalog.fallbackId(type.level()));
    }

    public static void toggle(DwBossType type) {
        if (type == null) {
            return;
        }

        Set<String> blacklist = ConfigManager.get().bossWidget.blacklist;
        if (blacklist == null) {
            blacklist = new LinkedHashSet<>();
            ConfigManager.get().bossWidget.blacklist = blacklist;
        }

        if (isBlocked(type, blacklist)) {
            remove(blacklist, type.id());
            if (type.level() > 0) {
                remove(blacklist, BossTypeCatalog.fallbackId(type.level()));
            }
        } else {
            String id = BossTypeCatalog.normalizeId(type.id());
            if (!id.isEmpty()) {
                blacklist.add(id);
            }
        }
        ConfigManager.save();
    }

    public static void clear() {
        ConfigManager.get().bossWidget.blacklist.clear();
        ConfigManager.save();
    }

    private static boolean contains(Set<String> values, String candidate) {
        String normalized = BossTypeCatalog.normalizeId(candidate);
        return !normalized.isEmpty() && values.stream().anyMatch(value -> normalized.equals(BossTypeCatalog.normalizeId(value)));
    }

    private static void remove(Set<String> values, String candidate) {
        String normalized = BossTypeCatalog.normalizeId(candidate);
        values.removeIf(value -> normalized.equals(BossTypeCatalog.normalizeId(value)));
    }
}
