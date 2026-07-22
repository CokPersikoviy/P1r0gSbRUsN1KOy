package ru.wilyfox.client.protocol;

import ru.wilyfox.boss.BossRepository;
import ru.wilyfox.client.ability.AbilityCooldownStore;
import ru.wilyfox.client.boss.BossDamageStore;
import ru.wilyfox.client.booster.BoosterStore;
import ru.wilyfox.client.combo.ComboProgressStore;
import ru.wilyfox.client.level.LevelProgressStore;
import ru.wilyfox.client.miner.ActiveMinersStore;
import ru.wilyfox.client.pet.ActivePetsStore;
import ru.wilyfox.client.potion.PotionStore;
import ru.wilyfox.client.rune.ActiveRunesStore;
import ru.wilyfox.client.seller.SellerCooldownStore;
import ru.wilyfox.client.wand.WandCooldownTracker;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class ProtocolState {
    static final int PAYLOAD_SAMPLE_LIMIT_PER_SUBCHANNEL = 3;

    boolean initialized;
    boolean loggedFirstMinerPayload;
    boolean receivedEvoPlusPayload;
    long lastHandshakeAt;
    long lastPayloadAt;
    long lastAbilityTimersAt;

    Map<String, DwBossType> bossTypes = new LinkedHashMap<>();
    Map<String, DwPetType> petTypes = new LinkedHashMap<>();
    Map<String, DwAbilityType> abilityTypes = new LinkedHashMap<>();
    Map<Integer, DwStaffType> staffTypes = new LinkedHashMap<>();
    Map<String, Long> lastAbilityTimers = new LinkedHashMap<>();
    Map<String, Integer> payloadSampleCounts = new LinkedHashMap<>();
    ProtocolDiagnostics diagnostics = new ProtocolDiagnostics();

    BossRepository bossRepository;
    ActiveRunesStore activeRunesStore;
    ActivePetsStore activePetsStore;
    ActiveMinersStore activeMinersStore;
    AbilityCooldownStore abilityCooldownStore;
    BossDamageStore bossDamageStore;
    LevelProgressStore levelProgressStore;
    PotionStore potionStore;
    SellerCooldownStore sellerCooldownStore;
    ComboProgressStore comboProgressStore;
    BoosterStore boosterStore;
    WandCooldownTracker wandCooldownTracker;

    CurrentServerInfo currentServerInfo = CurrentServerInfo.unknown();
    DwGameEvent currentGameEvent = DwGameEvent.NONE;
    DwClanState clanInfo = DwClanState.empty();
    Set<Integer> capturedBossLevels = Set.of();
    DwClanSiegePosition clanSiegePosition = DwClanSiegePosition.UNAVAILABLE;
    Map<String, Set<String>> bossCollectibles = new LinkedHashMap<>();
    DwGameLocation currentGameLocation;
    Set<String> fishingLocationIds = new LinkedHashSet<>();
    Map<String, String> fishingLocationNames = new LinkedHashMap<>();
    Map<String, Double> fishingNibbles = new LinkedHashMap<>();
    Map<Integer, DwHourlyQuestType> hourlyQuestTypes = new LinkedHashMap<>();
    Map<Integer, DwHourlyQuestProgress> hourlyQuestProgress = new LinkedHashMap<>();

    void resetRuntimeState() {
        receivedEvoPlusPayload = false;
        lastHandshakeAt = 0L;
        lastPayloadAt = 0L;
        lastAbilityTimersAt = 0L;
        loggedFirstMinerPayload = false;

        bossTypes = new LinkedHashMap<>();
        petTypes = new LinkedHashMap<>();
        abilityTypes = new LinkedHashMap<>();
        staffTypes = new LinkedHashMap<>();
        lastAbilityTimers = new LinkedHashMap<>();
        payloadSampleCounts = new LinkedHashMap<>();
        diagnostics = new ProtocolDiagnostics();

        currentServerInfo = CurrentServerInfo.unknown();
        currentGameEvent = DwGameEvent.NONE;
        clanInfo = DwClanState.empty();
        capturedBossLevels = Set.of();
        clanSiegePosition = DwClanSiegePosition.UNAVAILABLE;
        bossCollectibles = new LinkedHashMap<>();
        currentGameLocation = null;
        fishingLocationIds = new LinkedHashSet<>();
        fishingLocationNames = new LinkedHashMap<>();
        fishingNibbles = new LinkedHashMap<>();
        hourlyQuestTypes = new LinkedHashMap<>();
        hourlyQuestProgress = new LinkedHashMap<>();
    }
}
