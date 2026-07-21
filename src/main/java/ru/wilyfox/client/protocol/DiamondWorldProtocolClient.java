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

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiamondWorldProtocolClient {
    private static final Set<String> MANUAL_FISHING_LOCATION_IDS = Set.of(
            "bay",
            "swamp",
            "citycanal",
            "ambergrot",
            "azurepond",
            "basalt",
            "netherval",
            "magma",
            "endwharf",
            "silence",
            "crystal"
    );
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

    /** Boss ids inside a clan-info "bosses" JSON array, e.g. ["heraldOfHell","brutalPiglin"]. */
    private static final Pattern CAPTURED_BOSS_ID = Pattern.compile("\"([^\"]+)\"");
    // Parse result cache: the claninfo "bosses" string only changes ~every couple of minutes, but this
    // used to regex-parse + allocate a fresh Set on EVERY call — and BossHudWidget calls it per boss per
    // measurement per frame. Cache the parsed set, keyed on the raw string, and reuse the instance.
    private static String cachedCapturedBossesRaw;
    private static Set<Integer> cachedCapturedLevels = Set.of();

    /**
     * Levels of the bosses the player's clan currently holds ("captured") — parsed from the claninfo
     * {@code bosses} list and mapped through {@link DwBossType}. A held boss is being captured / its
     * location is occupied. Empty if not in a clan or nothing is held. Returned set is shared/read-only.
     */
    public static Set<Integer> getCapturedBossLevels() {
        String bosses = STATE.clanInfo.get("bosses");
        if (bosses == null || bosses.isBlank()) {
            cachedCapturedBossesRaw = bosses;
            cachedCapturedLevels = Set.of();
            return cachedCapturedLevels;
        }
        if (bosses.equals(cachedCapturedBossesRaw)) {
            return cachedCapturedLevels; // clan data unchanged since last parse — reuse
        }

        Set<Integer> levels = new HashSet<>();
        Matcher matcher = CAPTURED_BOSS_ID.matcher(bosses);
        while (matcher.find()) {
            DwBossType type = STATE.bossTypes.get(matcher.group(1));
            if (type != null) {
                levels.add(type.level());
            }
        }
        cachedCapturedBossesRaw = bosses;
        cachedCapturedLevels = levels.isEmpty() ? Set.of() : levels;
        return cachedCapturedLevels;
    }

    public static String getCurrentGameLocation() {
        return STATE.currentGameLocation;
    }

    public static boolean isDungeonLocation() {
        String location = normalizeLocationId(STATE.currentGameLocation);
        return location != null && location.contains("dungeon");
    }

    public static boolean isSiegeLocation() {
        String location = normalizeLocationId(STATE.currentGameLocation);
        return location != null && location.contains("siege");
    }

    public static boolean isDungeonOrSiegeLocation() {
        return isDungeonLocation() || isSiegeLocation();
    }

    public static Set<String> getFishingLocationIds() {
        return Set.copyOf(STATE.fishingLocationIds);
    }

    public static boolean isCurrentFishingLocation() {
        return isFishingLocation(STATE.currentGameLocation);
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

    public static String getFishingLocationName(String locationId) {
        String normalized = normalizeLocationId(locationId);
        if (normalized == null) {
            return "";
        }

        String name = STATE.fishingLocationNames.get(normalized);
        return name != null && !name.isBlank() ? name : normalized;
    }

    public static String getBossNameByLevel(int level) {
        for (DwBossType type : STATE.bossTypes.values()) {
            if (type.level() == level) {
                return type.name();
            }
        }

        return null;
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

    private static boolean isFishingLocation(String value) {
        String normalized = normalizeLocationId(value);
        if (normalized == null) {
            return false;
        }

        if (MANUAL_FISHING_LOCATION_IDS.contains(normalized)) {
            return true;
        }

        String compactCurrent = compactLocationKey(normalized);
        if (compactCurrent == null) {
            return false;
        }

        for (String locationId : STATE.fishingLocationIds) {
            if (compactCurrent.equals(compactLocationKey(locationId))) {
                return true;
            }
        }

        for (Map.Entry<String, String> entry : STATE.fishingLocationNames.entrySet()) {
            if (compactCurrent.equals(compactLocationKey(entry.getKey()))
                    || compactCurrent.equals(compactLocationKey(entry.getValue()))) {
                return true;
            }
        }

        return false;
    }

    private static String compactLocationKey(String value) {
        String normalized = normalizeLocationId(value);
        if (normalized == null) {
            return null;
        }

        String compact = normalized.replaceAll("[^\\p{L}\\p{N}]+", "");
        return compact.isBlank() ? null : compact;
    }
}
