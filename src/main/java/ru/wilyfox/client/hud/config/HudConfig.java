package ru.wilyfox.client.hud.config;

import java.util.LinkedHashMap;
import java.util.Map;

import ru.wilyfox.client.quickaccess.QuickAccessConfig;

public class HudConfig {
    public Integer lastWindowWidth;
    public Integer lastWindowHeight;
    public RenderConfig render = new RenderConfig();
    public AutoMessagesConfig autoMessages = new AutoMessagesConfig();
    public BossWidgetConfig bossWidget = new BossWidgetConfig();
    public ClickerConfig clicker = new ClickerConfig();
    public BlocksPerSecondWidgetConfig blocksPerSecondWidget = new BlocksPerSecondWidgetConfig();
    public EstimatedTpsConfig estimatedTps = new EstimatedTpsConfig();
    public FishingConfig fishing = new FishingConfig();
    public BossBarConfig bossBar = new BossBarConfig();
    public ScoreboardConfig scoreboard = new ScoreboardConfig();
    public PlayerHealthBarsConfig playerHealthBars = new PlayerHealthBarsConfig();
    public AlchemyConfig alchemy = new AlchemyConfig();
    public PotionRecipeConfig potionRecipe = new PotionRecipeConfig();
    public CraftRecipeConfig craftRecipe = new CraftRecipeConfig();
    public PotionTimersConfig potionTimers = new PotionTimersConfig();
    public SellerCooldownConfig sellerCooldown = new SellerCooldownConfig();
    public ComboProgressConfig comboProgress = new ComboProgressConfig();
    public WandCooldownConfig wandCooldown = new WandCooldownConfig();
    public AbilityCooldownConfig abilityCooldown = new AbilityCooldownConfig();
    public ActiveRunesConfig activeRunes = new ActiveRunesConfig();
    public RunesBagConfig runesBag = new RunesBagConfig();
    public ActivePetsConfig activePets = new ActivePetsConfig();
    public ActiveMinersConfig activeMiners = new ActiveMinersConfig();
    public BossDamageConfig bossDamage = new BossDamageConfig();
    public VisibilityStatusConfig visibilityStatus = new VisibilityStatusConfig();
    public DungeonMapConfig dungeonMap = new DungeonMapConfig();
    public EntityInspectConfig entityInspect = new EntityInspectConfig();
    public OutgoingChatQueueConfig outgoingChatQueue = new OutgoingChatQueueConfig();
    public ProtocolGraphWidgetConfig protocolGraphWidget = new ProtocolGraphWidgetConfig();
    public LevelProgressConfig levelProgress = new LevelProgressConfig();
    public PopUpsConfig popUps = new PopUpsConfig();
    public BoostersConfig boosters = new BoostersConfig();
    public BossRespawnMessagesConfig bossRespawnMessages = new BossRespawnMessagesConfig();
    public DiscordRpcConfig discordRpc = new DiscordRpcConfig();
    public QuickAccessConfig quickAccess = new QuickAccessConfig();
    public ThemeConfig theme = new ThemeConfig();
    public Map<String, WidgetLayoutConfig> widgetLayouts = new LinkedHashMap<>();
}
