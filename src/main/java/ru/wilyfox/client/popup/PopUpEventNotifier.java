package ru.wilyfox.client.popup;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import ru.wilyfox.boss.BossInfo;
import ru.wilyfox.boss.BossRepository;
import ru.wilyfox.client.ability.AbilityCooldownStore;
import ru.wilyfox.client.booster.BoosterStore;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.miner.ActiveMinerInfo;
import ru.wilyfox.client.miner.ActiveMinersStore;
import ru.wilyfox.client.potion.PotionStore;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.rune.RuneSetCooldownStore;
import ru.wilyfox.client.seller.SellerCooldownStore;
import ru.wilyfox.client.wand.WandCooldownTracker;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PopUpEventNotifier {
    private static final long BOSS_SPAWN_WINDOW_MS = 2_000L;
    private static final PopUpEventNotifier INSTANCE = new PopUpEventNotifier();

    private final Map<String, Long> announcedBossRespawns = new HashMap<>();
    private final Map<String, String> previousAbilityNames = new LinkedHashMap<>();
    private final Map<String, String> previousWandNames = new LinkedHashMap<>();
    private final Map<String, SellerCooldownStore.Entry> previousSellerEntries = new LinkedHashMap<>();
    private final Map<String, ActiveMinerInfo> previousMiners = new LinkedHashMap<>();
    private final Map<Integer, PotionStore.ActivePotionEntry> previousPotions = new LinkedHashMap<>();
    private final Map<String, BoosterStateSnapshot> previousBoosters = new LinkedHashMap<>();

    private BossRepository bossRepository;
    private AbilityCooldownStore abilityCooldownStore;
    private ActiveMinersStore activeMinersStore;
    private SellerCooldownStore sellerCooldownStore;
    private PotionStore potionStore;
    private BoosterStore boosterStore;
    private WandCooldownTracker wandCooldownTracker;

    private boolean registered;
    private boolean primed;
    private boolean previousRuneSetActive;

    private PopUpEventNotifier() {
    }

    public static PopUpEventNotifier getInstance() {
        return INSTANCE;
    }

    public void bindBossRepository(BossRepository repository) {
        this.bossRepository = repository;
    }

    public void bindAbilityCooldownStore(AbilityCooldownStore store) {
        this.abilityCooldownStore = store;
    }

    public void bindActiveMinersStore(ActiveMinersStore store) {
        this.activeMinersStore = store;
    }

    public void bindSellerCooldownStore(SellerCooldownStore store) {
        this.sellerCooldownStore = store;
    }

    public void bindPotionStore(PotionStore store) {
        this.potionStore = store;
    }

    public void bindBoosterStore(BoosterStore store) {
        this.boosterStore = store;
    }

    public void bindWandCooldownTracker(WandCooldownTracker tracker) {
        this.wandCooldownTracker = tracker;
    }

    public void register() {
        if (registered) {
            return;
        }

        registered = true;

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("tick/PopUpEventNotifier")) {
                if (client.player == null || client.getConnection() == null) {
                    return;
                }

                if (!primed) {
                    prime();
                    return;
                }

                checkBossSpawns();
                checkAbilityReady();
                checkWandReady();
                checkSellerReady();
                checkMinerReturned();
                checkRuneSetReady();
                checkPotionExpired();
                checkBoosterExpired();
            }
        });
    }

    private void prime() {
        primed = true;
        previousRuneSetActive = RuneSetCooldownStore.isActive();
        previousAbilityNames.clear();
        if (abilityCooldownStore != null) {
            for (AbilityCooldownStore.Entry entry : abilityCooldownStore.getActiveEntries()) {
                previousAbilityNames.put(entry.id(), entry.name());
            }
        }

        previousWandNames.clear();
        if (wandCooldownTracker != null) {
            for (WandCooldownTracker.WandCooldownEntry entry : wandCooldownTracker.getActiveEntries()) {
                previousWandNames.put(entry.key(), entry.name());
            }
        }

        previousSellerEntries.clear();
        if (sellerCooldownStore != null) {
            for (SellerCooldownStore.Entry entry : sellerCooldownStore.getEntries()) {
                previousSellerEntries.put(entry.id(), entry);
            }
        }

        previousMiners.clear();
        if (activeMinersStore != null) {
            for (ActiveMinerInfo miner : activeMinersStore.getAll()) {
                previousMiners.put(minerKey(miner), miner);
            }
        }

        previousPotions.clear();
        if (potionStore != null) {
            for (PotionStore.ActivePotionEntry entry : potionStore.getActiveEntries()) {
                previousPotions.put(entry.id(), entry);
            }
        }

        previousBoosters.clear();
        if (boosterStore != null) {
            captureBoosterSnapshots(previousBoosters);
        }
    }

    private void checkBossSpawns() {
        if (bossRepository == null) {
            return;
        }

        long now = System.currentTimeMillis();
        for (BossInfo boss : bossRepository.getAllMerged()) {
            String key = bossKey(boss);
            long respawnAt = boss.getRespawnAt();
            long remaining = respawnAt - now;

            Long lastAnnounced = announcedBossRespawns.get(key);
            if (lastAnnounced != null && lastAnnounced.longValue() != respawnAt) {
                announcedBossRespawns.remove(key);
                lastAnnounced = null;
            }

            if (remaining <= 0L && remaining >= -BOSS_SPAWN_WINDOW_MS && lastAnnounced == null) {
                PopUpManager.getInstance().publish(PopUpRequest.of(
                        PopUpSource.BOSS_SPAWN,
                        "Boss Respawned",
                        formatBossLabel(boss) + " appeared",
                        PopUpSeverity.WARNING
                ));
                announcedBossRespawns.put(key, respawnAt);
            }

            if (remaining > BOSS_SPAWN_WINDOW_MS + 5_000L) {
                announcedBossRespawns.remove(key);
            }
        }
    }

    private void checkAbilityReady() {
        if (abilityCooldownStore == null) {
            return;
        }

        Map<String, String> current = new LinkedHashMap<>();
        for (AbilityCooldownStore.Entry entry : abilityCooldownStore.getActiveEntries()) {
            current.put(entry.id(), entry.name());
        }

        for (Map.Entry<String, String> previous : previousAbilityNames.entrySet()) {
            if (!current.containsKey(previous.getKey())) {
                PopUpManager.getInstance().publish(PopUpRequest.of(
                        PopUpSource.ABILITY_READY,
                        "Ability Ready",
                        previous.getValue() + " can be used again",
                        PopUpSeverity.SUCCESS
                ));
            }
        }

        previousAbilityNames.clear();
        previousAbilityNames.putAll(current);
    }

    private void checkWandReady() {
        if (wandCooldownTracker == null) {
            return;
        }

        Map<String, String> current = new LinkedHashMap<>();
        for (WandCooldownTracker.WandCooldownEntry entry : wandCooldownTracker.getActiveEntries()) {
            current.put(entry.key(), entry.name());
        }

        for (Map.Entry<String, String> previous : previousWandNames.entrySet()) {
            if (!current.containsKey(previous.getKey())) {
                if (!shouldNotifyWandReady(previous.getValue())) {
                    continue;
                }

                PopUpManager.getInstance().publish(PopUpRequest.of(
                        PopUpSource.WAND_READY,
                        "Staff Recharged",
                        previous.getValue() + " is ready",
                        PopUpSeverity.SUCCESS
                ));
            }
        }

        previousWandNames.clear();
        previousWandNames.putAll(current);
    }

    private boolean shouldNotifyWandReady(String wandName) {
        if (wandName == null || wandName.isBlank()) {
            return true;
        }

        if (WandCooldownTracker.isWindStaffName(wandName) && !ConfigManager.get().popUps.windStaffReadyEvent) {
            return false;
        }

        return true;
    }

    private void checkSellerReady() {
        if (sellerCooldownStore == null) {
            return;
        }

        Map<String, SellerCooldownStore.Entry> current = new LinkedHashMap<>();
        for (SellerCooldownStore.Entry entry : sellerCooldownStore.getEntries()) {
            current.put(entry.id(), entry);

            SellerCooldownStore.Entry previous = previousSellerEntries.get(entry.id());
            if (previous != null && !previous.ready() && entry.ready()) {
                PopUpManager.getInstance().publish(PopUpRequest.of(
                        PopUpSource.SELLER_READY,
                        "Seller Ready",
                        entry.name() + " is available now",
                        PopUpSeverity.INFO
                ));
            }
        }

        previousSellerEntries.clear();
        previousSellerEntries.putAll(current);
    }

    private void checkMinerReturned() {
        if (activeMinersStore == null) {
            return;
        }

        Map<String, ActiveMinerInfo> current = new LinkedHashMap<>();
        for (ActiveMinerInfo miner : activeMinersStore.getAll()) {
            String key = minerKey(miner);
            current.put(key, miner);

            ActiveMinerInfo previous = previousMiners.get(key);
            if (previous != null && !isMinerReturned(previous) && isMinerReturned(miner)) {
                PopUpManager.getInstance().publish(PopUpRequest.of(
                        PopUpSource.MINER_RETURNED,
                        "Miner Returned",
                        formatMinerLabel(miner) + " returned home",
                        PopUpSeverity.INFO
                ));
            }
        }

        previousMiners.clear();
        previousMiners.putAll(current);
    }

    private void checkRuneSetReady() {
        boolean currentActive = RuneSetCooldownStore.isActive();
        if (previousRuneSetActive && !currentActive) {
            PopUpManager.getInstance().publish(PopUpRequest.of(
                    PopUpSource.RUNE_SET_READY,
                    "Rune Set Ready",
                    "Rune set can be switched again",
                    PopUpSeverity.SUCCESS
            ));
        }
        previousRuneSetActive = currentActive;
    }

    private void checkPotionExpired() {
        if (potionStore == null) {
            return;
        }

        Map<Integer, PotionStore.ActivePotionEntry> current = new LinkedHashMap<>();
        for (PotionStore.ActivePotionEntry entry : potionStore.getActiveEntries()) {
            current.put(entry.id(), entry);
        }

        for (Map.Entry<Integer, PotionStore.ActivePotionEntry> previous : previousPotions.entrySet()) {
            if (!current.containsKey(previous.getKey())) {
                PopUpManager.getInstance().publish(PopUpRequest.of(
                        PopUpSource.POTION_EXPIRED,
                        "Potion Expired",
                        previous.getValue().name() + " has ended",
                        PopUpSeverity.INFO
                ));
            }
        }

        previousPotions.clear();
        previousPotions.putAll(current);
    }

    private void checkBoosterExpired() {
        if (boosterStore == null) {
            return;
        }

        Map<String, BoosterStateSnapshot> current = new LinkedHashMap<>();
        captureBoosterSnapshots(current);

        for (Map.Entry<String, BoosterStateSnapshot> previous : previousBoosters.entrySet()) {
            if (!current.containsKey(previous.getKey())) {
                PopUpManager.getInstance().publish(PopUpRequest.of(
                        PopUpSource.BOOSTER_EXPIRED,
                        "Booster Ended",
                        previous.getValue().label() + " expired",
                        PopUpSeverity.INFO
                ));
            }
        }

        previousBoosters.clear();
        previousBoosters.putAll(current);
    }

    private void captureBoosterSnapshots(Map<String, BoosterStateSnapshot> target) {
        for (BoosterStore.Kind kind : BoosterStore.Kind.values()) {
            BoosterStore.Snapshot snapshot = boosterStore.getSnapshot(kind);
            List<BoosterStore.Entry> entries = snapshot.entries();
            for (int index = 0; index < entries.size(); index++) {
                putBooster(target, kind, entries.get(index), index);
            }
        }
    }

    private void putBooster(Map<String, BoosterStateSnapshot> target, BoosterStore.Kind kind, BoosterStore.Entry entry, int index) {
        if (entry == null) {
            return;
        }

        String key = kind.name() + "|" + index;
        String kindLabel = switch (kind) {
            case SHARD -> "Shards";
            case MONEY -> "Money";
            case SHAFT -> "Shaft";
        };
        String label = kindLabel + " Booster x" + entry.multiplier();
        target.put(key, new BoosterStateSnapshot(label));
    }

    private void reset() {
        primed = false;
        previousRuneSetActive = false;
        announcedBossRespawns.clear();
        previousAbilityNames.clear();
        previousWandNames.clear();
        previousSellerEntries.clear();
        previousMiners.clear();
        previousPotions.clear();
        previousBoosters.clear();
    }

    private boolean isMinerReturned(ActiveMinerInfo miner) {
        if (miner == null) {
            return false;
        }

        String status = miner.status() == null ? "" : miner.status().toUpperCase(Locale.ROOT);
        return status.contains("COMPLETE_TRAVEL") || (miner.homecomingAt() > 0L && miner.homecomingAt() <= System.currentTimeMillis());
    }

    private String minerKey(ActiveMinerInfo miner) {
        return miner.resource() + "#" + miner.level();
    }

    private String formatMinerLabel(ActiveMinerInfo miner) {
        return miner.level() > 0
                ? miner.resource() + " miner [Lv." + miner.level() + "]"
                : miner.resource() + " miner";
    }

    private String bossKey(BossInfo boss) {
        return boss.getName().trim().toLowerCase(Locale.ROOT) + "#" + boss.getLevel();
    }

    private String formatBossLabel(BossInfo boss) {
        if (boss.getLevel() > 0) {
            return boss.getName() + " [" + boss.getLevel() + "]";
        }
        return boss.getName();
    }

    private record BoosterStateSnapshot(String label) {
    }
}
