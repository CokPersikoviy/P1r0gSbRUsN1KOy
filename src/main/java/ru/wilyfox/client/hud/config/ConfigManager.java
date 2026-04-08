package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ru.wilyfox.client.hud.widget.AbstractWidget;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.quickaccess.QuickAccessConfig;
import ru.wilyfox.client.target.TargetListConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.error;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("froghelper.json");
    private static HudConfig CONFIG = load();

    public static HudConfig get() {
        return CONFIG;
    }

    public static synchronized void save() {
        CONFIG = sanitize(CONFIG);
        WidgetTheme.syncConfiguredTheme();

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(CONFIG, writer);
            }
        } catch (IOException exception) {
            error(LOGGER, "Failed to save FrogHelper config to {}", CONFIG_PATH, exception);
        }
    }

    public static synchronized WidgetLayoutConfig getWidgetLayout(String key) {
        return CONFIG.widgetLayouts.get(key);
    }

    public static synchronized Integer getLastWindowWidth() {
        return CONFIG.lastWindowWidth;
    }

    public static synchronized Integer getLastWindowHeight() {
        return CONFIG.lastWindowHeight;
    }

    public static synchronized void saveWindowSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        if (Integer.valueOf(width).equals(CONFIG.lastWindowWidth) && Integer.valueOf(height).equals(CONFIG.lastWindowHeight)) {
            return;
        }

        CONFIG.lastWindowWidth = width;
        CONFIG.lastWindowHeight = height;
        save();
    }

    public static synchronized void saveWidgetLayout(AbstractWidget widget) {
        if (widget == null || widget.getConfigKey() == null || widget.getConfigKey().isBlank()) {
            return;
        }

        WidgetLayoutConfig layout = CONFIG.widgetLayouts.computeIfAbsent(widget.getConfigKey(), ignored -> new WidgetLayoutConfig());
        layout.x = widget.getStartX();
        layout.y = widget.getStartY();
        layout.scale = widget.getScale();
        layout.anchor = widget.getScreenAnchor();
        layout.snapTarget = widget.getSnapTargetKey();
        layout.snapOwnCorner = widget.getSnapOwnCorner();
        layout.snapTargetCorner = widget.getSnapTargetCorner();
        layout.hiddenInGameplay = widget.isHiddenInGameplay();
        save();
    }

    private static HudConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            return sanitize(new HudConfig());
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            return sanitize(GSON.fromJson(reader, HudConfig.class));
        } catch (Exception exception) {
            error(LOGGER, "Failed to load FrogHelper config from {}", CONFIG_PATH, exception);
            return sanitize(new HudConfig());
        }
    }

    private static HudConfig sanitize(HudConfig config) {
        HudConfig sanitized = config != null ? config : new HudConfig();

        if (sanitized.render == null) sanitized.render = new RenderConfig();
        if (sanitized.autoMessages == null) sanitized.autoMessages = new AutoMessagesConfig();
        if (sanitized.bossWidget == null) sanitized.bossWidget = new BossWidgetConfig();
        if (sanitized.clicker == null) sanitized.clicker = new ClickerConfig();
        if (sanitized.blocksPerSecondWidget == null) sanitized.blocksPerSecondWidget = new BlocksPerSecondWidgetConfig();
        if (sanitized.estimatedTps == null) sanitized.estimatedTps = new EstimatedTpsConfig();
        if (sanitized.fishing == null) sanitized.fishing = new FishingConfig();
        if (sanitized.bossBar == null) sanitized.bossBar = new BossBarConfig();
        if (sanitized.scoreboard == null) sanitized.scoreboard = new ScoreboardConfig();
        if (sanitized.playerHealthBars == null) sanitized.playerHealthBars = new PlayerHealthBarsConfig();
        if (sanitized.potionRecipe == null) sanitized.potionRecipe = new PotionRecipeConfig();
        if (sanitized.craftRecipe == null) sanitized.craftRecipe = new CraftRecipeConfig();
        if (sanitized.potionTimers == null) sanitized.potionTimers = new PotionTimersConfig();
        if (sanitized.sellerCooldown == null) sanitized.sellerCooldown = new SellerCooldownConfig();
        if (sanitized.comboProgress == null) sanitized.comboProgress = new ComboProgressConfig();
        if (sanitized.wandCooldown == null) sanitized.wandCooldown = new WandCooldownConfig();
        if (sanitized.abilityCooldown == null) sanitized.abilityCooldown = new AbilityCooldownConfig();
        if (sanitized.activeRunes == null) sanitized.activeRunes = new ActiveRunesConfig();
        if (sanitized.activePets == null) sanitized.activePets = new ActivePetsConfig();
        if (sanitized.activeMiners == null) sanitized.activeMiners = new ActiveMinersConfig();
        if (sanitized.bossDamage == null) sanitized.bossDamage = new BossDamageConfig();
        if (sanitized.visibilityStatus == null) sanitized.visibilityStatus = new VisibilityStatusConfig();
        if (sanitized.dungeonMap == null) sanitized.dungeonMap = new DungeonMapConfig();
        if (sanitized.entityInspect == null) sanitized.entityInspect = new EntityInspectConfig();
        if (sanitized.outgoingChatQueue == null) sanitized.outgoingChatQueue = new OutgoingChatQueueConfig();
        if (sanitized.levelProgress == null) sanitized.levelProgress = new LevelProgressConfig();
        if (sanitized.popUps == null) sanitized.popUps = new PopUpsConfig();
        if (sanitized.boosters == null) sanitized.boosters = new BoostersConfig();
        if (sanitized.bossRespawnMessages == null) sanitized.bossRespawnMessages = new BossRespawnMessagesConfig();
        if (sanitized.discordRpc == null) sanitized.discordRpc = new DiscordRpcConfig();
        if (sanitized.wayPoints == null) sanitized.wayPoints = new WayPointsConfig();
        if (sanitized.quickAccess == null) sanitized.quickAccess = new QuickAccessConfig();
        if (sanitized.targets == null) sanitized.targets = new TargetListConfig();
        if (sanitized.theme == null) sanitized.theme = new ThemeConfig();
        if (sanitized.widgetLayouts == null) sanitized.widgetLayouts = new java.util.LinkedHashMap<>();
        if (sanitized.quickAccess.sections == null) sanitized.quickAccess.sections = QuickAccessConfig.createDefaultSections();
        if (sanitized.quickAccess.sections.isEmpty()) sanitized.quickAccess.sections = QuickAccessConfig.createDefaultSections();
        if (sanitized.targets.players == null) sanitized.targets.players = new java.util.ArrayList<>();
        if (sanitized.autoMessages.entries == null) {
            sanitized.autoMessages.entries = AutoMessagesConfig.createDefaultEntries();
        }
        while (sanitized.autoMessages.entries.size() < 1) {
            sanitized.autoMessages.entries.add(new AutoMessageEntryConfig());
        }
        for (int i = 0; i < sanitized.autoMessages.entries.size(); i++) {
            if (sanitized.autoMessages.entries.get(i) == null) {
                sanitized.autoMessages.entries.set(i, new AutoMessageEntryConfig());
            }
            AutoMessageEntryConfig entry = sanitized.autoMessages.entries.get(i);
            if (entry.message == null) entry.message = "";
            entry.delaySeconds = Math.max(1, entry.delaySeconds);
        }

        if (sanitized.discordRpc.clientId == null) sanitized.discordRpc.clientId = "";
        if (sanitized.discordRpc.largeImageKey == null) sanitized.discordRpc.largeImageKey = "froghelper";
        if (sanitized.discordRpc.largeImageText == null) sanitized.discordRpc.largeImageText = "FrogHelper";
        sanitized.discordRpc.updateIntervalSeconds = Math.max(1, Math.min(60, sanitized.discordRpc.updateIntervalSeconds));

        WidgetTheme.syncConfiguredTheme();
        return sanitized;
    }
}
