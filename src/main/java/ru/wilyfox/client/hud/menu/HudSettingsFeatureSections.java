package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import ru.wilyfox.client.discord.DiscordRpcService;
import ru.wilyfox.client.hud.config.BossTimerSourceMode;
import ru.wilyfox.client.hud.config.BossWidgetConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.FishingNibblesSort;
import ru.wilyfox.client.hud.config.FishingQuestDescriptionMode;
import ru.wilyfox.client.hud.config.FishingQuestTypeFilter;
import ru.wilyfox.client.hud.config.FishingWidgetVisibility;
import ru.wilyfox.client.hud.config.ThemePreset;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.quickaccess.QuickAccessScreen;

import java.util.List;
import java.util.Map;

final class HudSettingsFeatureSections {
    private HudSettingsFeatureSections() {
    }

    static void populate(
            Map<SettingsCategory, List<SettingsComponent>> componentsByCategory,
            Runnable rebuildAutoMessageComponents
    ) {
        for (int i = 0; i < 7; i++) {
            final int index = i;
            componentsByCategory.get(SettingsCategory.RUNES_BAG_KEYBINDS).add(
                    new KeybindSettingsComponent(
                            0, 0, 0, 0,
                            "Select Rune Set " + (i + 1),
                            () -> ConfigManager.get().runesBag.setSelectorKeys[index],
                            key -> ConfigManager.get().runesBag.setSelectorKeys[index] = key
                    )
            );
        }

        componentsByCategory.get(SettingsCategory.ALCHEMY).add(new BreakLineSettingsComponent("World"));
        componentsByCategory.get(SettingsCategory.ALCHEMY).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Ingredient markers",
                        () -> ConfigManager.get().render.showAlchemyIngredientMarkers,
                        value -> ConfigManager.get().render.showAlchemyIngredientMarkers = value
                )
        );
        componentsByCategory.get(SettingsCategory.ALCHEMY).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Auto-brewing cost",
                        () -> ConfigManager.get().alchemy.autoBrewingCost,
                        value -> ConfigManager.get().alchemy.autoBrewingCost = value
                )
        );
        componentsByCategory.get(SettingsCategory.ALCHEMY).add(new BreakLineSettingsComponent("Recipe alerts"));
        componentsByCategory.get(SettingsCategory.ALCHEMY).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Action alerts",
                        () -> ConfigManager.get().alchemy.recipeActionAlerts,
                        value -> ConfigManager.get().alchemy.recipeActionAlerts = value
                )
        );
        componentsByCategory.get(SettingsCategory.ALCHEMY).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Alert lead (ms)",
                        () -> ConfigManager.get().alchemy.recipeActionLeadMillis,
                        value -> ConfigManager.get().alchemy.recipeActionLeadMillis = value,
                        100, 2_000, 100
                ).withVisibility(() -> ConfigManager.get().alchemy.recipeActionAlerts)
        );
        componentsByCategory.get(SettingsCategory.ALCHEMY).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Alert sound",
                        () -> ConfigManager.get().alchemy.recipeActionSound,
                        value -> ConfigManager.get().alchemy.recipeActionSound = value
                ).withVisibility(() -> ConfigManager.get().alchemy.recipeActionAlerts)
        );
        componentsByCategory.get(SettingsCategory.ALCHEMY).add(new BreakLineSettingsComponent("Potion effects"));
        componentsByCategory.get(SettingsCategory.ALCHEMY).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Active potions in TAB",
                        () -> ConfigManager.get().alchemy.potionsInTab,
                        value -> ConfigManager.get().alchemy.potionsInTab = value
                )
        );
        componentsByCategory.get(SettingsCategory.ALCHEMY).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Expiration message in chat",
                        () -> ConfigManager.get().alchemy.potionExpirationChat,
                        value -> ConfigManager.get().alchemy.potionExpirationChat = value
                )
        );

        componentsByCategory.get(SettingsCategory.QUICK_ACCESS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Active",
                        () -> ConfigManager.get().quickAccess.active,
                        value -> ConfigManager.get().quickAccess.active = value
                )
        );

        componentsByCategory.get(SettingsCategory.QUICK_ACCESS).add(
                new ActionSettingsComponent(
                        "Open Editor",
                        () -> {
                            Minecraft minecraft = Minecraft.getInstance();
                            minecraft.gui.setScreen(QuickAccessScreen.editor(minecraft.gui.screen()));
                        }
                )
        );

        rebuildAutoMessageComponents.run();

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Active",
                        () -> ConfigManager.get().bossWidget.active,
                        value -> ConfigManager.get().bossWidget.active = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Keep Spawned Until Killed",
                        () -> ConfigManager.get().bossWidget.showSpawnedUntilKilled,
                        value -> ConfigManager.get().bossWidget.showSpawnedUntilKilled = value
                )
        );
        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Keep After Spawn (s)",
                        () -> ConfigManager.get().bossWidget.postSpawnShowSeconds,
                        value -> ConfigManager.get().bossWidget.postSpawnShowSeconds = value,
                        0, 600
                ).withVisibility(() -> !ConfigManager.get().bossWidget.showSpawnedUntilKilled)
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new CycleSettingsComponent<>(
                        0, 0, 0, 0,
                        "Timer source",
                        () -> ConfigManager.get().bossWidget.sourceMode,
                        value -> ConfigManager.get().bossWidget.sourceMode = value,
                        BossTimerSourceMode.values(),
                        BossTimerSourceMode::getTitle
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Max bosses",
                        () -> ConfigManager.get().bossWidget.maxBosses,
                        value -> ConfigManager.get().bossWidget.maxBosses = value,
                        1, 50, 1
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Min level",
                        () -> ConfigManager.get().bossWidget.minLevel,
                        value -> ConfigManager.get().bossWidget.minLevel = value,
                        15, HudSettingsFeatureSections::bossLevelCeiling, 5
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Max level",
                        () -> ConfigManager.get().bossWidget.maxLevel,
                        value -> ConfigManager.get().bossWidget.maxLevel = value,
                        15, HudSettingsFeatureSections::bossLevelCeiling, 5
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show name",
                        () -> ConfigManager.get().bossWidget.showName,
                        value -> ConfigManager.get().bossWidget.showName = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show icons",
                        () -> ConfigManager.get().bossWidget.showIcons,
                        value -> ConfigManager.get().bossWidget.showIcons = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show level",
                        () -> ConfigManager.get().bossWidget.showLevel,
                        value -> ConfigManager.get().bossWidget.showLevel = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show timer",
                        () -> ConfigManager.get().bossWidget.showTimer,
                        value -> ConfigManager.get().bossWidget.showTimer = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new BossBlacklistSettingsComponent()
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show collectibles",
                        () -> ConfigManager.get().bossWidget.showCollectibles,
                        value -> ConfigManager.get().bossWidget.showCollectibles = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_TIMERS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Full alignment",
                        () -> ConfigManager.get().bossWidget.fullAligment,
                        value -> ConfigManager.get().bossWidget.fullAligment = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Pre-respawn message",
                        () -> ConfigManager.get().bossRespawnMessages.preRespawnMessage,
                        value -> ConfigManager.get().bossRespawnMessages.preRespawnMessage = value
                )
        );
        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Pre-respawn clan message",
                        () -> ConfigManager.get().bossRespawnMessages.preRespawnClanMessage,
                        value -> ConfigManager.get().bossRespawnMessages.preRespawnClanMessage = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Pre-respawn seconds",
                        () -> ConfigManager.get().bossRespawnMessages.preRespawnSeconds,
                        value -> ConfigManager.get().bossRespawnMessages.preRespawnSeconds = value,
                        0, 360, 5
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Spawned message",
                        () -> ConfigManager.get().bossRespawnMessages.spawnMessage,
                        value -> ConfigManager.get().bossRespawnMessages.spawnMessage = value
                )
        );
        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Spawned clan message",
                        () -> ConfigManager.get().bossRespawnMessages.spawnClanMessage,
                        value -> ConfigManager.get().bossRespawnMessages.spawnClanMessage = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Curse message",
                        () -> ConfigManager.get().bossRespawnMessages.curseMessage,
                        value -> ConfigManager.get().bossRespawnMessages.curseMessage = value
                )
        );
        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Curse clan message",
                        () -> ConfigManager.get().bossRespawnMessages.curseClanMessage,
                        value -> ConfigManager.get().bossRespawnMessages.curseClanMessage = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Low HP message",
                        () -> ConfigManager.get().bossRespawnMessages.lowHealthMessage,
                        value -> ConfigManager.get().bossRespawnMessages.lowHealthMessage = value
                )
        );
        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Low HP clan message",
                        () -> ConfigManager.get().bossRespawnMessages.lowHealthClanMessage,
                        value -> ConfigManager.get().bossRespawnMessages.lowHealthClanMessage = value
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Low HP percent",
                        () -> ConfigManager.get().bossRespawnMessages.lowHealthPercent,
                        value -> ConfigManager.get().bossRespawnMessages.lowHealthPercent = value,
                        1, 100, 1
                )
        );

        componentsByCategory.get(SettingsCategory.BOSS_RESPAWN_MESSAGES).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Low HP cooldown",
                        () -> ConfigManager.get().bossRespawnMessages.lowHealthCooldownSeconds,
                        value -> ConfigManager.get().bossRespawnMessages.lowHealthCooldownSeconds = value,
                        1, 60, 1
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Active",
                        () -> ConfigManager.get().playerHealthBars.active,
                        value -> ConfigManager.get().playerHealthBars.active = value
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show numeric HP",
                        () -> ConfigManager.get().playerHealthBars.showNumericHp,
                        value -> ConfigManager.get().playerHealthBars.showNumericHp = value
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Offset",
                        () -> ConfigManager.get().playerHealthBars.verticalOffset,
                        value -> ConfigManager.get().playerHealthBars.verticalOffset = value,
                        -60, 60
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Opacity %",
                        () -> ConfigManager.get().playerHealthBars.opacityPercent,
                        value -> ConfigManager.get().playerHealthBars.opacityPercent = value,
                        10, 100
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Distance fade",
                        () -> ConfigManager.get().playerHealthBars.distanceFade,
                        value -> ConfigManager.get().playerHealthBars.distanceFade = value
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Bar size %",
                        () -> ConfigManager.get().playerHealthBars.sizePercent,
                        value -> ConfigManager.get().playerHealthBars.sizePercent = value,
                        50, 250
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Hard accent threshold %",
                        () -> ConfigManager.get().playerHealthBars.hardAccentThresholdPercent,
                        value -> ConfigManager.get().playerHealthBars.hardAccentThresholdPercent = value,
                        0, 100
                )
        );

        componentsByCategory.get(SettingsCategory.PLAYER_HEALTH_BARS).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "Accent strength %",
                        () -> ConfigManager.get().playerHealthBars.accentStrengthPercent,
                        value -> ConfigManager.get().playerHealthBars.accentStrengthPercent = value,
                        0, 100
                )
        );

        componentsByCategory.get(SettingsCategory.CLICKER).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "CPS",
                        () -> ConfigManager.get().clicker.cps,
                        value -> ConfigManager.get().clicker.cps = value,
                        1, 20
                )
        );

        componentsByCategory.get(SettingsCategory.CLICKER).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Use item",
                        () -> ConfigManager.get().clicker.useItem,
                        value -> ConfigManager.get().clicker.useItem = value
                )
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Pet experience in inventory",
                        () -> ConfigManager.get().fishing.showPetExperienceOverlay,
                        value -> ConfigManager.get().fishing.showPetExperienceOverlay = value
                )
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show markers",
                        () -> ConfigManager.get().fishing.showFishingMarkers,
                        value -> ConfigManager.get().fishing.showFishingMarkers = value
                )
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show fishing nibbles widget",
                        () -> ConfigManager.get().fishing.showFishingNibblesWidget,
                        value -> ConfigManager.get().fishing.showFishingNibblesWidget = value
                )
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new CycleSettingsComponent<>(0, 0, 0, 0, "Nibbles visibility",
                        () -> ConfigManager.get().fishing.nibblesVisibility,
                        value -> ConfigManager.get().fishing.nibblesVisibility = value,
                        FishingWidgetVisibility.values(), FishingWidgetVisibility::displayName)
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new CycleSettingsComponent<>(0, 0, 0, 0, "Nibbles sorting",
                        () -> ConfigManager.get().fishing.nibblesSort,
                        value -> ConfigManager.get().fishing.nibblesSort = value,
                        FishingNibblesSort.values(), FishingNibblesSort::displayName)
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new ToggleSettingsComponent(0, 0, 0, 0, "Show fishing quests widget",
                        () -> ConfigManager.get().fishing.showFishingQuestsWidget,
                        value -> ConfigManager.get().fishing.showFishingQuestsWidget = value)
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new CycleSettingsComponent<>(0, 0, 0, 0, "Quests visibility",
                        () -> ConfigManager.get().fishing.questsVisibility,
                        value -> ConfigManager.get().fishing.questsVisibility = value,
                        FishingWidgetVisibility.values(), FishingWidgetVisibility::displayName)
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new CycleSettingsComponent<>(0, 0, 0, 0, "Quest dimension",
                        () -> ConfigManager.get().fishing.questsTypeFilter,
                        value -> ConfigManager.get().fishing.questsTypeFilter = value,
                        FishingQuestTypeFilter.values(), FishingQuestTypeFilter::displayName)
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new CycleSettingsComponent<>(0, 0, 0, 0, "Quest descriptions",
                        () -> ConfigManager.get().fishing.questsDescription,
                        value -> ConfigManager.get().fishing.questsDescription = value,
                        FishingQuestDescriptionMode.values(), FishingQuestDescriptionMode::displayName)
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new ToggleSettingsComponent(0, 0, 0, 0, "Higher nibble notification",
                        () -> ConfigManager.get().fishing.higherBitingNotification,
                        value -> ConfigManager.get().fishing.higherBitingNotification = value)
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "AutoFish",
                        () -> ConfigManager.get().fishing.autoFish,
                        value -> ConfigManager.get().fishing.autoFish = value
                )
        );

        componentsByCategory.get(SettingsCategory.FISHING).add(
                new SliderSettingsComponent(
                        0, 0, 0, 0,
                        "AutoFish delay",
                        () -> ConfigManager.get().fishing.autoFishDelayTicks,
                        value -> ConfigManager.get().fishing.autoFishDelayTicks = value,
                        0, 40
                )
        );

        for (ThemePreset preset : ThemePreset.values()) {
            componentsByCategory.get(SettingsCategory.THEME).add(
                    new ThemePresetSettingsComponent(0, 0, 0, 0, preset)
            );
        }

        componentsByCategory.get(SettingsCategory.THEME).add(
                new ColorPickerSettingsComponent(
                        "Primary color",
                        () -> rgb(
                                ConfigManager.get().theme.customAccentRed,
                                ConfigManager.get().theme.customAccentGreen,
                                ConfigManager.get().theme.customAccentBlue
                        ),
                        color -> {
                            ConfigManager.get().theme.customAccentRed = red(color);
                            ConfigManager.get().theme.customAccentGreen = green(color);
                            ConfigManager.get().theme.customAccentBlue = blue(color);
                        }
                )
        );
        componentsByCategory.get(SettingsCategory.THEME).add(
                new ColorPickerSettingsComponent(
                        "Secondary color",
                        () -> rgb(
                                ConfigManager.get().theme.customSecondaryRed,
                                ConfigManager.get().theme.customSecondaryGreen,
                                ConfigManager.get().theme.customSecondaryBlue
                        ),
                        color -> {
                            ConfigManager.get().theme.customSecondaryRed = red(color);
                            ConfigManager.get().theme.customSecondaryGreen = green(color);
                            ConfigManager.get().theme.customSecondaryBlue = blue(color);
                        }
                )
        );

        componentsByCategory.get(SettingsCategory.THEME).add(new BreakLineSettingsComponent("Hard Accent"));
        componentsByCategory.get(SettingsCategory.THEME).add(
                new ColorPickerSettingsComponent(
                        "Hard accent",
                        () -> rgb(
                                ConfigManager.get().theme.hardAccentRed,
                                ConfigManager.get().theme.hardAccentGreen,
                                ConfigManager.get().theme.hardAccentBlue
                        ),
                        color -> {
                            ConfigManager.get().theme.hardAccentRed = red(color);
                            ConfigManager.get().theme.hardAccentGreen = green(color);
                            ConfigManager.get().theme.hardAccentBlue = blue(color);
                        }
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Active",
                        () -> ConfigManager.get().popUps.active,
                        value -> ConfigManager.get().popUps.active = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Visible at once",
                        () -> ConfigManager.get().popUps.maxVisible,
                        value -> ConfigManager.get().popUps.maxVisible = value,
                        1, 6, 1
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Hold ms",
                        () -> ConfigManager.get().popUps.holdMillis,
                        value -> ConfigManager.get().popUps.holdMillis = value,
                        500, 10000, 100
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Fade-in ms",
                        () -> ConfigManager.get().popUps.fadeInMillis,
                        value -> ConfigManager.get().popUps.fadeInMillis = value,
                        0, 3000, 20
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new StepperSettingsComponent(
                        0, 0, 0, 0,
                        "Fade-out ms",
                        () -> ConfigManager.get().popUps.fadeOutMillis,
                        value -> ConfigManager.get().popUps.fadeOutMillis = value,
                        0, 3000, 20
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Chat copied",
                        () -> ConfigManager.get().popUps.chatCopyEvent,
                        value -> ConfigManager.get().popUps.chatCopyEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Private message",
                        () -> ConfigManager.get().popUps.privateMessageEvent,
                        value -> ConfigManager.get().popUps.privateMessageEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Boss spawned",
                        () -> ConfigManager.get().popUps.bossSpawnEvent,
                        value -> ConfigManager.get().popUps.bossSpawnEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Boss captured",
                        () -> ConfigManager.get().popUps.bossCaptureEvent,
                        value -> ConfigManager.get().popUps.bossCaptureEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Game event",
                        () -> ConfigManager.get().popUps.gameEvent,
                        value -> ConfigManager.get().popUps.gameEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Ability ready",
                        () -> ConfigManager.get().popUps.abilityReadyEvent,
                        value -> ConfigManager.get().popUps.abilityReadyEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Staff ready",
                        () -> ConfigManager.get().popUps.wandReadyEvent,
                        value -> ConfigManager.get().popUps.wandReadyEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Wind Staff",
                        () -> ConfigManager.get().popUps.windStaffReadyEvent,
                        value -> ConfigManager.get().popUps.windStaffReadyEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Seller ready",
                        () -> ConfigManager.get().popUps.sellerReadyEvent,
                        value -> ConfigManager.get().popUps.sellerReadyEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Miner returned",
                        () -> ConfigManager.get().popUps.minerReturnedEvent,
                        value -> ConfigManager.get().popUps.minerReturnedEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Level requirements",
                        () -> ConfigManager.get().popUps.levelReadyEvent,
                        value -> ConfigManager.get().popUps.levelReadyEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Rune set ready",
                        () -> ConfigManager.get().popUps.runeSetReadyEvent,
                        value -> ConfigManager.get().popUps.runeSetReadyEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Potion expired",
                        () -> ConfigManager.get().popUps.potionExpiredEvent,
                        value -> ConfigManager.get().popUps.potionExpiredEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Booster expired",
                        () -> ConfigManager.get().popUps.boosterExpiredEvent,
                        value -> ConfigManager.get().popUps.boosterExpiredEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Barrel found",
                        () -> ConfigManager.get().popUps.barrelFoundEvent,
                        value -> ConfigManager.get().popUps.barrelFoundEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.POP_UPS).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Event: Golden crystal found",
                        () -> ConfigManager.get().popUps.goldenCrystalFoundEvent,
                        value -> ConfigManager.get().popUps.goldenCrystalFoundEvent = value
                )
        );

        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new StatusSettingsComponent(
                        "RPC status",
                        DiscordRpcService::getStatus
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Privacy mode",
                        () -> ConfigManager.get().discordRpc.privacyMode,
                        value -> ConfigManager.get().discordRpc.privacyMode = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show elapsed time",
                        () -> ConfigManager.get().discordRpc.showElapsedTime,
                        value -> ConfigManager.get().discordRpc.showElapsedTime = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show server",
                        () -> ConfigManager.get().discordRpc.showServer,
                        value -> ConfigManager.get().discordRpc.showServer = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show mirror",
                        () -> ConfigManager.get().discordRpc.showMirror,
                        value -> ConfigManager.get().discordRpc.showMirror = value
                ).withIndent(18).withVisibility(() -> ConfigManager.get().discordRpc.showServer)
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show location",
                        () -> ConfigManager.get().discordRpc.showLocation,
                        value -> ConfigManager.get().discordRpc.showLocation = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show level",
                        () -> ConfigManager.get().discordRpc.showLevel,
                        value -> ConfigManager.get().discordRpc.showLevel = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show combo",
                        () -> ConfigManager.get().discordRpc.showCombo,
                        value -> ConfigManager.get().discordRpc.showCombo = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Show game event",
                        () -> ConfigManager.get().discordRpc.showGameEvent,
                        value -> ConfigManager.get().discordRpc.showGameEvent = value
                )
        );
        componentsByCategory.get(SettingsCategory.DISCORD).add(
                new ToggleSettingsComponent(
                        0, 0, 0, 0,
                        "Enable auto RPC",
                        () -> ConfigManager.get().discordRpc.active,
                        value -> ConfigManager.get().discordRpc.active = value
                )
        );
    }

    private static int bossLevelCeiling() {
        return Math.max(BossWidgetConfig.MAX_LEVEL_CEILING, DiamondWorldProtocolClient.getHighestKnownBossLevel());
    }

    private static int rgb(int red, int green, int blue) {
        return (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }

    private static int red(int color) {
        return color >> 16 & 0xFF;
    }

    private static int green(int color) {
        return color >> 8 & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }
}
