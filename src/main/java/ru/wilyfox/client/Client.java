package ru.wilyfox.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.resources.ResourceLocation;
import ru.wilyfox.boss.BossRepository;
import ru.wilyfox.boss.BossTracker;
import ru.wilyfox.client.ability.AbilityCooldownStore;
import ru.wilyfox.client.alchemy.AlchemyBrewingTracker;
import ru.wilyfox.client.boss.BossMenuIconCollector;
import ru.wilyfox.client.boss.BossDamageStore;
import ru.wilyfox.client.booster.BoosterStore;
import ru.wilyfox.client.chat.BossShareService;
import ru.wilyfox.client.chat.AutoBossAnnouncer;
import ru.wilyfox.client.chat.AutoMessageScheduler;
import ru.wilyfox.client.chat.ChatDispatchQueue;
import ru.wilyfox.client.chat.FrogChatProtocol;
import ru.wilyfox.client.chat.VisibilityStatusTracker;
import ru.wilyfox.client.clan.PlayerClanStorage;
import ru.wilyfox.client.combo.ComboProgressStore;
import ru.wilyfox.client.combo.ComboTimerChatTracker;
import ru.wilyfox.client.dungeon.DungeonDecorationHighlightRenderHook;
import ru.wilyfox.client.dungeon.DungeonMapTracker;
import ru.wilyfox.client.discord.DiscordRpcService;
import ru.wilyfox.client.event.ClientEntityEventHandler;
import ru.wilyfox.client.highlight.UsefulWorldHighlightRenderHook;
import ru.wilyfox.client.hud.HudRenderer;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.healthbar.PlayerHealthBarRenderHook;
import ru.wilyfox.client.hud.indicators.ScreenAnchor;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.menu.HudSettingsPanel;
import ru.wilyfox.client.hud.widget.ActiveMinersWidget;
import ru.wilyfox.client.hud.widget.ActivePetsWidget;
import ru.wilyfox.client.hud.widget.ActiveRunesWidget;
import ru.wilyfox.client.hud.widget.AbilityCooldownWidget;
import ru.wilyfox.client.hud.widget.BlocksPerSecondWidget;
import ru.wilyfox.client.hud.widget.BossBarWidget;
import ru.wilyfox.client.hud.widget.BossDamageWidget;
import ru.wilyfox.client.hud.widget.BossHudWidget;
import ru.wilyfox.client.hud.widget.BoostersWidget;
import ru.wilyfox.client.hud.widget.ComboProgressWidget;
import ru.wilyfox.client.hud.widget.CraftRecipeWidget;
import ru.wilyfox.client.hud.widget.EntityInspectWidget;
import ru.wilyfox.client.hud.widget.EstimatedTpsWidget;
import ru.wilyfox.client.hud.widget.FishingNibblesWidget;
import ru.wilyfox.client.hud.widget.FishingQuestsWidget;
import ru.wilyfox.client.hud.widget.DungeonMapWidget;
import ru.wilyfox.client.hud.widget.LevelProgressWidget;
import ru.wilyfox.client.hud.widget.OutgoingChatQueueWidget;
import ru.wilyfox.client.hud.widget.PopUpsWidget;
import ru.wilyfox.client.hud.widget.PotionRecipeWidget;
import ru.wilyfox.client.hud.widget.PotionTimersWidget;
import ru.wilyfox.client.hud.widget.ProtocolGraphWidget;
import ru.wilyfox.client.hud.widget.ScoreboardWidget;
import ru.wilyfox.client.hud.widget.SellerCooldownWidget;
import ru.wilyfox.client.hud.widget.WandCooldownWidget;
import ru.wilyfox.client.hud.widget.VisibilityStatusWidget;
import ru.wilyfox.client.level.LevelProgressStore;
import ru.wilyfox.client.keybinds.KeyBinds;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.miner.ActiveMinersStore;
import ru.wilyfox.client.pet.ActivePetsStore;
import ru.wilyfox.client.potion.PotionStore;
import ru.wilyfox.client.ping.PingMarkerManager;
import ru.wilyfox.client.ping.PingMarkerRenderHook;
import ru.wilyfox.client.popup.PopUpEventNotifier;
import ru.wilyfox.client.performance.EstimatedTpsMonitor;
import ru.wilyfox.client.quickaccess.QuickAccessInputHandler;
import ru.wilyfox.client.rune.ActiveRunesStore;
import ru.wilyfox.client.rune.RuneSetSwitcher;
import ru.wilyfox.client.seller.SellerCooldownStore;
import ru.wilyfox.client.utility.Clicker;
import ru.wilyfox.client.utility.AutoFish;
import ru.wilyfox.client.utility.HudInputHandler;
import ru.wilyfox.client.utility.MouseInputHandler;
import ru.wilyfox.client.utility.MousePingInputHandler;
import ru.wilyfox.client.visibility.VisibilityStatusStore;
import ru.wilyfox.client.wand.WandCooldownTracker;

import static ru.wilyfox.FrogHelper.MOD_ID;

public class Client {
    private static Client INSTANCE;

    private final BossRepository repository = new BossRepository();
    private final BossTracker bossTracker = new BossTracker(repository);
    private final ActiveRunesStore activeRunesStore = new ActiveRunesStore();
    private final ActivePetsStore activePetsStore = new ActivePetsStore();
    private final ActiveMinersStore activeMinersStore = new ActiveMinersStore();
    private final AbilityCooldownStore abilityCooldownStore = new AbilityCooldownStore();
    private final BossDamageStore bossDamageStore = new BossDamageStore();
    private final VisibilityStatusStore visibilityStatusStore = new VisibilityStatusStore();
    private final LevelProgressStore levelProgressStore = new LevelProgressStore();
    private final BoosterStore boosterStore = new BoosterStore();
    private final PotionStore potionStore = new PotionStore();
    private final SellerCooldownStore sellerCooldownStore = new SellerCooldownStore();
    private final ComboProgressStore comboProgressStore = new ComboProgressStore();
    private final WandCooldownTracker wandCooldownTracker = new WandCooldownTracker();
    private final HudSettingsPanel settingsPanel = new HudSettingsPanel();
    private final HudRenderer hudRenderer = new HudRenderer(settingsPanel);

    public Client() {
        INSTANCE = this;
    }

    public static Client getInstance() {
        return INSTANCE;
    }

    public HudRenderer getHudRenderer() {
        return hudRenderer;
    }

    public WandCooldownTracker getWandCooldownTracker() {
        return wandCooldownTracker;
    }

    public PotionStore getPotionStore() {
        return potionStore;
    }

    public void init() {
        ru.wilyfox.client.command.FhCommands.register();
        new ClientEntityEventHandler(this.bossTracker).register();
        new HudInputHandler(hudRenderer).register();
        new MouseInputHandler(hudRenderer).register();
        // new MousePingInputHandler().register();
        new QuickAccessInputHandler().register();
        DungeonMapTracker.getInstance().register();

        PlayerHealthBarRenderHook.register();
        DungeonDecorationHighlightRenderHook.register();
        UsefulWorldHighlightRenderHook.register();
        // PingMarkerManager.init();
        // PingMarkerRenderHook.register();
        EstimatedTpsMonitor.register();
        KeyBinds.register();
        RuneSetSwitcher.register();
        Clicker.register();
        AutoFish.register();
        AlchemyBrewingTracker.register();
        ChatDispatchQueue.init();
        AutoMessageScheduler.getInstance().register();
        AutoBossAnnouncer.bindRepository(repository);
        AutoBossAnnouncer.register();
        PlayerClanStorage.init();
        ru.wilyfox.client.moduser.ModUserStorage.init();
        ru.wilyfox.client.moduser.ModUserProtocol.init();
        PopUpEventNotifier.getInstance().bindBossRepository(repository);
        PopUpEventNotifier.getInstance().bindAbilityCooldownStore(abilityCooldownStore);
        PopUpEventNotifier.getInstance().bindActiveMinersStore(activeMinersStore);
        PopUpEventNotifier.getInstance().bindLevelProgressStore(levelProgressStore);
        PopUpEventNotifier.getInstance().bindSellerCooldownStore(sellerCooldownStore);
        PopUpEventNotifier.getInstance().bindPotionStore(potionStore);
        PopUpEventNotifier.getInstance().bindBoosterStore(boosterStore);
        PopUpEventNotifier.getInstance().bindWandCooldownTracker(wandCooldownTracker);
        PopUpEventNotifier.getInstance().register();
        BossShareService.bindRepository(repository);
        VisibilityStatusTracker.bindStore(visibilityStatusStore);
        ComboTimerChatTracker.bindStore(comboProgressStore);
        FrogChatProtocol.init();
        BossMenuIconCollector.bindRepository(repository);
        DiamondWorldProtocolClient.bindBossRepository(repository);
        DiamondWorldProtocolClient.bindActiveRunesStore(activeRunesStore);
        DiamondWorldProtocolClient.bindActivePetsStore(activePetsStore);
        DiamondWorldProtocolClient.bindActiveMinersStore(activeMinersStore);
        DiamondWorldProtocolClient.bindAbilityCooldownStore(abilityCooldownStore);
        DiamondWorldProtocolClient.bindBossDamageStore(bossDamageStore);
        DiamondWorldProtocolClient.bindLevelProgressStore(levelProgressStore);
        DiamondWorldProtocolClient.bindPotionStore(potionStore);
        DiamondWorldProtocolClient.bindSellerCooldownStore(sellerCooldownStore);
        DiamondWorldProtocolClient.bindComboProgressStore(comboProgressStore);
        DiamondWorldProtocolClient.bindBoosterStore(boosterStore);
        DiamondWorldProtocolClient.bindWandCooldownTracker(wandCooldownTracker);
        DiamondWorldProtocolClient.init();
        DiscordRpcService.bindLevelProgressStore(levelProgressStore);
        DiscordRpcService.bindComboProgressStore(comboProgressStore);
        DiscordRpcService.bindBossDamageStore(bossDamageStore);
        DiscordRpcService.register();
        final ResourceLocation FrogHelperLayer = ResourceLocation.fromNamespaceAndPath(MOD_ID, "hud-froghelper-layer");
        final ResourceLocation FrogHelperSettingsLayer = ResourceLocation.fromNamespaceAndPath(MOD_ID, "hud-froghelper-settings-layer");

        HudLayerRegistrationCallback.EVENT.register(layeredDrawer -> {
            // Widgets + editor overlay render before chat; the settings panel renders after chat (above it).
            layeredDrawer.attachLayerBefore(IdentifiedLayer.CHAT, FrogHelperLayer, this.hudRenderer::render);
            layeredDrawer.attachLayerAfter(IdentifiedLayer.CHAT, FrogHelperSettingsLayer, this.hudRenderer::renderSettingsOverlay);
        });

        hudRenderer.registerWidget(
                new BossHudWidget(5, 5, HudLayer.CONTENT, repository),
                ScreenAnchor.TOP_LEFT
        );
        hudRenderer.registerWidget(
                new ru.wilyfox.client.hud.widget.GroupHudWidget(60, 60, HudLayer.CONTENT),
                ScreenAnchor.TOP_RIGHT
        );
        hudRenderer.registerWidget(
                new BlocksPerSecondWidget(100, 100, HudLayer.CONTENT),
                ScreenAnchor.HOTBAR_LEFT
        );
        hudRenderer.registerWidget(
                new EstimatedTpsWidget(100, 130, HudLayer.CONTENT),
                ScreenAnchor.HOTBAR_LEFT
        );
        hudRenderer.registerWidget(
                new BoostersWidget(20, 20, HudLayer.CONTENT, boosterStore),
                ScreenAnchor.TOP_RIGHT
        );
        hudRenderer.registerWidget(
                new PotionRecipeWidget(20, 20, HudLayer.CONTENT),
                ScreenAnchor.BOTTOM_RIGHT
        );
        hudRenderer.registerWidget(
                new CraftRecipeWidget(20, 52, HudLayer.CONTENT),
                ScreenAnchor.BOTTOM_RIGHT
        );
        hudRenderer.registerWidget(
                new PotionTimersWidget(20, 45, HudLayer.CONTENT, potionStore),
                ScreenAnchor.TOP_RIGHT
        );
        hudRenderer.registerWidget(
                new SellerCooldownWidget(20, 75, HudLayer.CONTENT, sellerCooldownStore),
                ScreenAnchor.TOP_RIGHT
        );
        hudRenderer.registerWidget(
                new ComboProgressWidget(100, 120, HudLayer.CONTENT, comboProgressStore),
                ScreenAnchor.HOTBAR_LEFT
        );
        hudRenderer.registerWidget(
                new BossBarWidget(50, 3, HudLayer.CONTENT),
                ScreenAnchor.TOP_CENTER
        );
        hudRenderer.registerWidget(
                new ScoreboardWidget(0, 0, HudLayer.CONTENT),
                ScreenAnchor.RIGHT_CENTER
        );
        hudRenderer.registerWidget(
                new WandCooldownWidget(20, 60, HudLayer.CONTENT, wandCooldownTracker),
                ScreenAnchor.HOTBAR_RIGHT
        );
        hudRenderer.registerWidget(
                new ActiveRunesWidget(20, 90, HudLayer.CONTENT, activeRunesStore),
                ScreenAnchor.LEFT_CENTER
        );
        hudRenderer.registerWidget(
                new ActivePetsWidget(20, 120, HudLayer.CONTENT, activePetsStore),
                ScreenAnchor.TOP_RIGHT
        );
        hudRenderer.registerWidget(
                new ActiveMinersWidget(20, 150, HudLayer.CONTENT, activeMinersStore),
                ScreenAnchor.RIGHT_CENTER
        );
        hudRenderer.registerWidget(
                new AbilityCooldownWidget(20, 150, HudLayer.CONTENT, abilityCooldownStore),
                ScreenAnchor.BOTTOM_LEFT
        );
        hudRenderer.registerWidget(
                new BossDamageWidget(20, 180, HudLayer.CONTENT, bossDamageStore),
                ScreenAnchor.BOTTOM_LEFT
        );
        hudRenderer.registerWidget(
                new VisibilityStatusWidget(20, 210, HudLayer.CONTENT, visibilityStatusStore),
                ScreenAnchor.BOTTOM_LEFT
        );
        hudRenderer.registerWidget(
                new DungeonMapWidget(20, 240, HudLayer.CONTENT),
                ScreenAnchor.RIGHT_CENTER
        );
        hudRenderer.registerWidget(
                new FishingNibblesWidget(20, 380, HudLayer.CONTENT),
                ScreenAnchor.RIGHT_CENTER
        );
        hudRenderer.registerWidget(
                new FishingQuestsWidget(20, 410, HudLayer.CONTENT),
                ScreenAnchor.RIGHT_CENTER
        );
        hudRenderer.registerWidget(
                new EntityInspectWidget(20, 520, HudLayer.CONTENT),
                ScreenAnchor.BOTTOM_LEFT
        );
        hudRenderer.registerWidget(
                new OutgoingChatQueueWidget(20, 270, HudLayer.CONTENT),
                ScreenAnchor.BOTTOM_RIGHT
        );
        hudRenderer.registerWidget(
                new ProtocolGraphWidget(20, 300, HudLayer.CONTENT),
                ScreenAnchor.BOTTOM_RIGHT
        );
        hudRenderer.registerWidget(
                new PopUpsWidget(20, 340, HudLayer.CONTENT),
                ScreenAnchor.BOTTOM_RIGHT
        );
        hudRenderer.registerWidget(
                new LevelProgressWidget(20, 330, HudLayer.CONTENT, levelProgressStore),
                ScreenAnchor.TOP_RIGHT
        );

        hudRenderer.finalizeWidgetRegistration();
        ConfigManager.save();
    }
}
