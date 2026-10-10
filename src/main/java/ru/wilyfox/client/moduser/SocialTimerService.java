package ru.wilyfox.client.moduser;

import java.util.List;
import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.boss.BossRepository;
import ru.wilyfox.client.hud.config.BossTimerSourceMode;
import ru.wilyfox.client.hud.config.ConfigManager;

/** The network sees immutable timer DTOs; all repository access stays on the client thread. */
public final class SocialTimerService {
    private static BossRepository repository;
    private SocialTimerService() {}
    public static void bindRepository(BossRepository value) { repository = value; }
    static SocialWire.TimerUpload snapshot(String scope, long now) {
        var mode = ConfigManager.get().bossWidget.sourceMode;
        var bosses = repository == null ? List.<BossInfo>of() : switch (mode) {
            case WORLD_ONLY -> repository.getAllWorld();
            case PROTOCOL_ONLY -> repository.getAllProtocol();
            case PROTOCOL_PREFERRED -> repository.getAllMerged();
        };
        var timers = bosses.stream().filter(boss -> boss.getRespawnAt() > now && boss.getRespawnAt() <= now + 604_800_000
                && boss.getName() != null && !boss.getName().isBlank() && boss.getName().codePointCount(0, boss.getName().length()) <= 64)
                .limit(512).map(boss -> new SocialWire.Timer(boss.getName(), boss.getLevel(), boss.getRespawnAt()))
                .sorted(java.util.Comparator.comparing(SocialWire.Timer::name)).toList();
        return new SocialWire.TimerUpload(1, scope, mode == BossTimerSourceMode.PROTOCOL_ONLY, timers);
    }
    static int receive(List<SocialWire.Timer> timers) {
        return repository == null ? 0 : repository.importShared(timers.stream()
                .map(timer -> new BossInfo(timer.name(), timer.respawnAt(), timer.level())).toList());
    }
}
