package ru.wilyfox.client.protocol;

import net.minecraft.network.chat.Component;
import ru.wilyfox.boss.BossIconInfo;
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

import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static ru.wilyfox.utils.Formatting.stripMinecraftFormatting;

public final class DiamondWorldProtocolClient {
    private static final ProtocolState STATE = new ProtocolState();
    private static final ProtocolRouter ROUTER = new ProtocolRouter();

    private DiamondWorldProtocolClient() {
    }

    public static void init() {
        ProtocolTransport.init(STATE, ROUTER);
    }

    public static void bindBossRepository(BossRepository repository) {
        STATE.bossRepository = repository;
    }

    public static void bindActiveRunesStore(ActiveRunesStore store) {
        STATE.activeRunesStore = store;
    }

    public static void bindActivePetsStore(ActivePetsStore store) {
        STATE.activePetsStore = store;
    }

    public static void bindActiveMinersStore(ActiveMinersStore store) {
        STATE.activeMinersStore = store;
    }

    public static void bindAbilityCooldownStore(AbilityCooldownStore store) {
        STATE.abilityCooldownStore = store;
    }

    public static void bindBossDamageStore(BossDamageStore store) {
        STATE.bossDamageStore = store;
    }

    public static void bindLevelProgressStore(LevelProgressStore store) {
        STATE.levelProgressStore = store;
    }

    public static void bindPotionStore(PotionStore store) {
        STATE.potionStore = store;
    }

    public static void bindSellerCooldownStore(SellerCooldownStore store) {
        STATE.sellerCooldownStore = store;
    }

    public static void bindComboProgressStore(ComboProgressStore store) {
        STATE.comboProgressStore = store;
    }

    public static void bindBoosterStore(BoosterStore store) {
        STATE.boosterStore = store;
    }

    public static void bindWandCooldownTracker(WandCooldownTracker tracker) {
        STATE.wandCooldownTracker = tracker;
    }

    public static String getCurrentServerDisplayName(Component footer) {
        if (STATE.currentServerInfo != null && STATE.currentServerInfo.isKnown()) {
            return STATE.currentServerInfo.displayName();
        }

        if (footer != null) {
            CurrentServerInfo fallback = CurrentServerInfo.fromDisplayText(footer.getString());
            if (fallback.isKnown()) {
                return fallback.displayName();
            }
        }

        return "";
    }

    public static CurrentServerInfo getCurrentServerInfo() {
        return STATE.currentServerInfo != null ? STATE.currentServerInfo : CurrentServerInfo.unknown();
    }

    public static DwGameEvent getCurrentGameEvent() {
        return STATE.currentGameEvent != null ? STATE.currentGameEvent : DwGameEvent.NONE;
    }

    /** The local player's current DiamondWorld level (0 if not known yet). */
    public static int getCurrentLevel() {
        return STATE.levelProgressStore != null ? STATE.levelProgressStore.getSnapshot().level() : 0;
    }

    /** The local player's active ability cooldowns (empty if none/unavailable). */
    public static java.util.List<AbilityCooldownStore.Entry> getAbilityCooldowns() {
        return STATE.abilityCooldownStore != null ? STATE.abilityCooldownStore.getActiveEntries() : java.util.List.of();
    }

    public static boolean isMythicalEventActive() {
        return getCurrentGameEvent() == DwGameEvent.MYTHICAL_EVENT;
    }

    public static boolean isRaidBossLevel(int level) {
        if (level <= 0) {
            return false;
        }

        for (DwBossType type : STATE.bossTypes.values()) {
            if (type.level() == level) {
                return type.raid();
            }
        }

        return false;
    }

    /**
     * Optimistically refresh the active-runes HUD from the currently open rune bag. The bag inventory
     * syncs faster than the activerunes packet (which can lag seconds under server load), so this
     * removes the "old set lingers a few seconds after switching" freeze. Accepts an empty list (a
     * genuinely emptied set) — callers gate this on {@code RuneSetEffectOverlay.isRuneBagLoaded}.
     */
    public static void updateActiveRunesFromBag(List<String> runes) {
        if (STATE.activeRunesStore != null) {
            STATE.activeRunesStore.replace(runes);
        }
    }

    /**
     * Levels of the bosses the player's clan currently holds ("captured") — parsed from the claninfo
     * {@code bosses} list and mapped through {@link DwBossType}. A held boss is being captured / its
     * location is occupied. Empty if not in a clan or nothing is held. Returned set is shared/read-only.
     */
    public static Set<Integer> getCapturedBossLevels() {
        return STATE.capturedBossLevels;
    }

    public static String getCurrentClanName() {
        return STATE.clanInfo.name();
    }

    public static List<String> getCurrentClanMembers() {
        return STATE.clanInfo.members();
    }

    public static String getCurrentClanNameForMember(String playerName) {
        String clanName = STATE.clanInfo.name();
        return !clanName.isBlank() && STATE.clanInfo.containsMember(playerName) ? clanName : null;
    }

    public static DwClanSiegePosition getClanSiegePosition() {
        return STATE.clanSiegePosition;
    }

    public static String getCurrentGameLocation() {
        return STATE.currentGameLocation != null ? STATE.currentGameLocation.id() : null;
    }

    public static DwGameLocation getCurrentGameLocationData() {
        return STATE.currentGameLocation;
    }

    public static String getGameLocationDisplayName(String locationId) {
        DwGameLocation location = createGameLocation(locationId);
        if (location == null) {
            return "";
        }

        if (location.isFishing()) {
            String registeredName = STATE.fishingLocationNames.get(location.normalizedId());
            if (registeredName != null && !registeredName.isBlank()) {
                return registeredName;
            }
        }

        if (location.isBoss()) {
            String bossId = location.bossId();
            DwBossType type = bossId != null ? getBossTypeById(bossId) : null;
            return "\u0411\u043e\u0441\u0441 " + (type != null ? type.name() : location.id());
        }

        return location.displayName();
    }

    public static boolean isDungeonLocation() {
        return STATE.currentGameLocation != null && STATE.currentGameLocation.isDungeon();
    }

    public static boolean isProceduralDungeonLocation() {
        return STATE.currentGameLocation != null && STATE.currentGameLocation.isProceduralDungeon();
    }

    public static boolean isAnyDungeonLocation() {
        return STATE.currentGameLocation != null && STATE.currentGameLocation.isAnyDungeon();
    }

    public static boolean isSiegeLocation() {
        return STATE.currentGameLocation != null && STATE.currentGameLocation.isSiege();
    }

    public static boolean isDungeonOrSiegeLocation() {
        return isDungeonLocation() || isSiegeLocation();
    }

    public static Set<String> getFishingLocationIds() {
        return Set.copyOf(STATE.fishingLocationIds);
    }

    public static boolean isCurrentFishingLocation() {
        return STATE.currentGameLocation != null && STATE.currentGameLocation.isFishing();
    }

    public static boolean isCurrentAlchemyLocation() {
        return STATE.currentGameLocation != null && STATE.currentGameLocation.isAlchemy();
    }

    public static List<String> getDiagnosticsStats() {
        return STATE.diagnostics.buildStatsLines();
    }

    public static List<String> getDiagnosticsAnomalies() {
        return STATE.diagnostics.buildAnomalyLines();
    }

    public static void resetDiagnostics() {
        STATE.diagnostics.reset();
    }

    public static Map<String, Double> getFishingNibbles() {
        return Map.copyOf(STATE.fishingNibbles);
    }

    public static Map<String, String> getFishingLocationNames() {
        return Map.copyOf(STATE.fishingLocationNames);
    }

    public static boolean hasFishingNibbles() {
        return !STATE.fishingNibbles.isEmpty();
    }

    public static Map<Integer, DwHourlyQuestType> getHourlyQuestTypes() {
        return Map.copyOf(STATE.hourlyQuestTypes);
    }

    public static Map<Integer, DwHourlyQuestProgress> getHourlyQuestProgress() {
        return Map.copyOf(STATE.hourlyQuestProgress);
    }

    public static String getFishingLocationName(String locationId) {
        String normalized = normalizeLocationId(locationId);
        if (normalized == null) {
            return "";
        }

        String name = STATE.fishingLocationNames.get(normalized);
        return name != null && !name.isBlank() ? name : normalized;
    }

    public static String getBossNameByLevel(int level) {
        DwBossType type = getBossTypeByLevel(level);
        return type != null ? type.name() : null;
    }

    public static DwBossType getBossTypeByLevel(int level) {
        for (DwBossType type : STATE.bossTypes.values()) {
            if (type.level() == level) {
                return type;
            }
        }
        return null;
    }

    public static DwBossType getBossTypeById(String bossId) {
        if (bossId == null || bossId.isBlank()) {
            return null;
        }

        DwBossType exact = STATE.bossTypes.get(bossId);
        if (exact != null) {
            return exact;
        }

        for (Map.Entry<String, DwBossType> entry : STATE.bossTypes.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(bossId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static DwBossType getBossTypeByName(String bossName) {
        if (normalizeBossName(bossName).isEmpty()) {
            return null;
        }

        for (DwBossType type : STATE.bossTypes.values()) {
            if (bossNamesMatch(type.name(), bossName)) {
                return type;
            }
        }
        return null;
    }

    static boolean bossNamesMatch(String registeredName, String observedName) {
        String registered = normalizeBossName(registeredName);
        String observed = normalizeBossName(observedName);
        if (registered.equals(observed)) {
            return !registered.isEmpty();
        }
        return observed.equals("командир легиона")
                && (registered.equals("бессмертный легион") || registered.equals("бессмертныи легион"));
    }

    public static boolean isCurrentBossLocation() {
        return STATE.currentGameLocation != null && STATE.currentGameLocation.isBoss();
    }

    public static DwBossType getCurrentBossType() {
        String bossId = STATE.currentGameLocation != null ? STATE.currentGameLocation.bossId() : null;
        return bossId != null ? getBossTypeById(bossId) : null;
    }

    static String bossIdFromLocation(String location) {
        if (location == null) {
            return null;
        }

        location = location.trim();
        if (!location.regionMatches(true, 0, "boss", 0, 4) || location.length() <= 4) {
            return null;
        }
        int idStart = 4;
        while (idStart < location.length()) {
            char separator = location.charAt(idStart);
            if (separator != '_' && separator != '-' && separator != ':' && separator != '/' && !Character.isWhitespace(separator)) {
                break;
            }
            idStart++;
        }
        return idStart < location.length() ? location.substring(idStart) : null;
    }

    /** Null means that the level is unknown or intentionally excluded from collection tracking. */
    public static Boolean hasBossCollectibleByLevel(int level) {
        DwBossType type = getBossTypeByLevel(level);
        if (type == null || type.id().equalsIgnoreCase("guardian")) {
            return null;
        }

        Set<String> collection = STATE.bossCollectibles.get(type.id());
        if (collection == null) {
            for (Map.Entry<String, Set<String>> entry : STATE.bossCollectibles.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(type.id())) {
                    collection = entry.getValue();
                    break;
                }
            }
        }
        return collection != null && !collection.isEmpty();
    }

    /**
     * Highest boss level reported by the server via {@code bosstypes}, or 0 if none seen yet.
     * Lets the level filter/slider ceiling extend automatically when new high-level bosses are added.
     */
    public static int getHighestKnownBossLevel() {
        int highest = 0;
        for (DwBossType type : STATE.bossTypes.values()) {
            if (type.level() > highest) {
                highest = type.level();
            }
        }

        return highest;
    }

    public static BossIconInfo getBossIconByLevel(int level) {
        if (level <= 0) {
            return null;
        }

        for (DwBossType type : STATE.bossTypes.values()) {
            if (type.level() == level && type.material() != null && !type.material().isBlank()) {
                return new BossIconInfo(type.material(), type.customModelData());
            }
        }

        return null;
    }

    static String normalizeLocationId(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeBossName(String value) {
        if (value == null) {
            return "";
        }
        return stripMinecraftFormatting(value)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+[\\uE124\\uE125\\uE126](?:\\s+x\\d+)?", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    static boolean isFishingLocation(String value) {
        DwGameLocation location = createGameLocation(value);
        return location != null && location.isFishing();
    }

    static DwGameLocation createGameLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new DwGameLocation(value);
    }
}
