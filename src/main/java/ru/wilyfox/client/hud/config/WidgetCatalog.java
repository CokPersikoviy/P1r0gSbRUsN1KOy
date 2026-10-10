package ru.wilyfox.client.hud.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Stable IDs shared by the widget library, persisted layouts and legacy migration. */
public enum WidgetCatalog {
    BOSS_TIMERS("BossHudWidget", "Boss Timers", c -> c.bossWidget.active),
    BLOCK_COUNTER("BlocksPerSecondWidget", "Block Counter", c -> c.blocksPerSecondWidget.active),
    DAILY_BLOCKS("DailyBlocksWidget", "Daily Blocks", c -> c.dailyBlocks.active),
    ESTIMATED_TPS("EstimatedTpsWidget", "Estimated TPS", c -> c.estimatedTps.active),
    BOOSTERS("BoostersWidget", "Boosters", c -> c.boosters.active),
    CRAFT_RECIPE("CraftRecipeWidget", "Craft Recipe", c -> c.craftRecipe.active),
    POTION_TIMERS("PotionTimersWidget", "Potion Cooldowns", c -> c.potionTimers.active),
    SELLERS("SellerCooldownWidget", "Seller Cooldowns", c -> c.sellerCooldown.active),
    COMBO("ComboProgressWidget", "Combo Progress", c -> c.comboProgress.active),
    BOSS_BAR("BossBarWidget", "Boss Bar", c -> c.bossBar.active),
    SCOREBOARD("ScoreboardWidget", "Scoreboard", c -> c.scoreboard.active),
    WAND("WandCooldownWidget", "Wand Cooldowns", c -> c.wandCooldown.active),
    RUNES("ActiveRunesWidget", "Active Runes", c -> c.activeRunes.active),
    PETS("ActivePetsWidget", "Active Pets", c -> c.activePets.active),
    MINERS("ActiveMinersWidget", "Active Miners", c -> c.activeMiners.active),
    ABILITIES("AbilityCooldownWidget", "Ability Cooldowns", c -> c.abilityCooldown.active),
    EFFECTS("ActiveEffectsWidget", "Active Effects", c -> c.activeEffects.active),
    BOSS_DAMAGE("BossDamageWidget", "Boss Damage", c -> c.bossDamage.active),
    VISIBILITY("VisibilityStatusWidget", "Visibility Status", c -> c.visibilityStatus.active),
    MAP("DungeonMapWidget", "Dungeon / Siege Map", c -> c.dungeonMap.active),
    FISHING_NIBBLES("FishingNibblesWidget", "Fishing Nibbles", c -> c.fishing.showFishingNibblesWidget),
    FISHING_QUESTS("FishingQuestsWidget", "Fishing Quests", c -> c.fishing.showFishingQuestsWidget),
    ENTITY_INSPECT("EntityInspectWidget", "Entity Inspect", c -> c.entityInspect.active),
    CHAT_QUEUE("OutgoingChatQueueWidget", "Chat Queue", c -> c.outgoingChatQueue.active),
    PROTOCOL_GRAPH("ProtocolGraphWidget", "Protocol Graph", c -> c.protocolGraphWidget.active),
    POP_UPS("PopUpsWidget", "Pop-ups", c -> c.popUps.active),
    LEVEL_PROGRESS("LevelProgressWidget", "Level Progress", c -> c.levelProgress.active),
    CHAT_ALL("ChatWidgetAll", "Chat ALL", c -> false),
    CHAT_FH("ChatWidgetFH", "Chat FH", c -> false),
    CHAT_GLOBAL("ChatWidgetGlobal", "Chat G", c -> false),
    CHAT_TRADE("ChatWidgetTrade", "Chat T", c -> false),
    CHAT_LOCAL("ChatWidgetLocal", "Chat L", c -> false),
    CHAT_CLAN("ChatWidgetClan", "Chat C", c -> false),
    CHAT_PRIVATE("ChatWidgetPrivate", "Chat PM", c -> false);

    private static final Map<String, WidgetCatalog> BY_KEY = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(WidgetCatalog::key, entry -> entry));
    private static final java.util.regex.Pattern CUSTOM_CHAT_KEY = java.util.regex.Pattern.compile("ChatWidgetCustom_[a-f0-9-]{36}");

    private final String key;
    private final String title;
    private final Predicate<HudConfig> legacyEnabled;

    WidgetCatalog(String key, String title, Predicate<HudConfig> legacyEnabled) {
        this.key = key;
        this.title = title;
        this.legacyEnabled = legacyEnabled;
    }

    public String key() { return key; }
    public String title() { return title; }
    public ru.wilyfox.client.chat.ChatTab chatChannel() {
        return switch (this) {
            case CHAT_ALL -> ru.wilyfox.client.chat.ChatTab.ALL;
            case CHAT_FH -> ru.wilyfox.client.chat.ChatTab.FH;
            case CHAT_GLOBAL -> ru.wilyfox.client.chat.ChatTab.GLOBAL;
            case CHAT_TRADE -> ru.wilyfox.client.chat.ChatTab.TRADE;
            case CHAT_LOCAL -> ru.wilyfox.client.chat.ChatTab.LOCAL;
            case CHAT_CLAN -> ru.wilyfox.client.chat.ChatTab.CLAN;
            case CHAT_PRIVATE -> ru.wilyfox.client.chat.ChatTab.PRIVATE;
            default -> null;
        };
    }
    public static WidgetCatalog find(String key) {
        return key == null ? null : isCustomChatKey(key) ? CHAT_ALL : BY_KEY.get(key);
    }
    public static boolean isCustomChatKey(String key) {
        return key != null && key.startsWith("ChatWidgetCustom_") && CUSTOM_CHAT_KEY.matcher(key).matches();
    }

    public boolean isAdded() { return ConfigManager.isWidgetInCurrentLayout(key); }

    public static boolean isAdded(HudConfig config, String key) {
        return config.mainLayout != null && config.mainLayout.widgets != null
                && config.mainLayout.widgets.contains(key);
    }

    public static void sanitize(HudConfig config) {
        if (config.mainLayout == null) config.mainLayout = new MainWidgetLayoutConfig();
        if (config.mainLayout.widgets == null) config.mainLayout.widgets = new LinkedHashSet<>();
        config.mainLayout.widgets.removeIf(key -> find(key) == null);
    }

    /** Run only when loading an existing config without mainLayout, never for a new install. */
    public static void migrateLegacy(HudConfig config) {
        config.mainLayout = new MainWidgetLayoutConfig();
        for (WidgetCatalog entry : values()) {
            WidgetLayoutConfig placement = config.widgetLayouts.get(entry.key);
            boolean hidden = placement != null && Boolean.TRUE.equals(placement.hiddenInGameplay);
            if (entry.legacyEnabled.test(config) && !hidden) config.mainLayout.widgets.add(entry.key);
        }
    }
}
