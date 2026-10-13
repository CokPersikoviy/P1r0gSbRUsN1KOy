package ru.wilyfox.client.hud.menu;

import ru.wilyfox.client.hud.config.BossTimerSourceMode;
import ru.wilyfox.client.hud.config.BossWidgetConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.FishingNibblesSort;
import ru.wilyfox.client.hud.config.FishingQuestDescriptionMode;
import ru.wilyfox.client.hud.config.FishingQuestTypeFilter;
import ru.wilyfox.client.hud.config.FishingWidgetVisibility;
import ru.wilyfox.client.hud.config.SellerCooldownFilter;
import ru.wilyfox.client.hud.config.WidgetCatalog;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Widget content controls; global appearance and gameplay features stay in the main menu. */
final class WidgetSettingsSections {
    private WidgetSettingsSections() {}
    static List<SettingsComponent> create(WidgetCatalog widget) {
        return create(widget, widget.key());
    }
    static List<SettingsComponent> create(WidgetCatalog widget, String key) {
        var items = new ArrayList<SettingsComponent>();
        if (widget.chatChannel() != null) {
            Supplier<ru.wilyfox.client.hud.config.ChatWidgetConfig> config = () -> ConfigManager.get().chatWidgets.get(key);
            if (widget == WidgetCatalog.CHAT_FH) {
                items.add(slider("Chat width", () -> config.get().width, value -> config.get().width = value, 100, 600));
                items.add(slider("Chat rows", () -> config.get().rows, value -> config.get().rows = value, 2, 30));
                items.add(toggle("Show chat title", () -> config.get().showTitle, value -> config.get().showTitle = value));
                items.add(toggle("Show input bar", () -> config.get().showInput, value -> config.get().showInput = value));
                items.add(cycle("Widget visibility", () -> config.get().visibility, value -> config.get().visibility = value,
                        ru.wilyfox.client.hud.config.ChatWidgetVisibility.values(), ru.wilyfox.client.hud.config.ChatWidgetVisibility::title));
                items.add(toggle("Message sound", () -> config.get().messageSound, value -> config.get().messageSound = value));
                return items;
            }
            items.add(new TextInputSettingsComponent(0, 0, 0, 0, "Tab name", () -> config.get().title,
                    value -> config.get().title = value, 20));
            items.add(toggle("Regex filter", () -> config.get().regexFilter,
                    value -> { config.get().regexFilter = value; ConfigManager.layoutChanged(); }));
            items.add(new ChatFilterSettingsComponent(config));
            items.add(cycle("Channel", () -> config.get().channel, value -> { config.get().channel = value; ConfigManager.layoutChanged(); },
                    ru.wilyfox.client.chat.ChatTab.values(), ru.wilyfox.client.chat.ChatTab::getTitle));
            items.add(slider("Chat width", () -> config.get().width, value -> config.get().width = value, 100, 600));
            items.add(slider("Chat rows", () -> config.get().rows, value -> config.get().rows = value, 2, 30));
            items.add(toggle("Show chat title", () -> config.get().showTitle, value -> config.get().showTitle = value));
            items.add(cycle("Widget visibility", () -> config.get().visibility, value -> config.get().visibility = value,
                    ru.wilyfox.client.hud.config.ChatWidgetVisibility.values(), ru.wilyfox.client.hud.config.ChatWidgetVisibility::title));
            items.add(toggle("Message sound", () -> config.get().messageSound, value -> config.get().messageSound = value));
            items.add(toggle("Skip Others", () -> config.get().skipOthers,
                    value -> { config.get().skipOthers = value; ConfigManager.layoutChanged(); }));
            return items;
        }
        switch (widget) {
            case LEVEL_GOAL -> {
                items.add(slider("Target level (0 = not set)", () -> ConfigManager.get().levelGoal.targetLevel,
                        value -> { ConfigManager.get().levelGoal.targetLevel = value; ConfigManager.get().levelGoal.sanitize(); }, 0, ru.wilyfox.client.calculator.LevelCalculator.MAX_LEVEL));
                items.add(cycle("Goal server", () -> ConfigManager.get().levelGoal.server, value -> ConfigManager.get().levelGoal.server = value,
                        ru.wilyfox.client.hud.config.LevelGoalConfig.ServerMode.values(), Enum::name));
                items.add(new CycleSettingsComponent<>(0,0,0,0,"Goal discount", () -> ConfigManager.get().levelGoal.discount, value -> ConfigManager.get().levelGoal.discount = value,
                        () -> ru.wilyfox.client.calculator.LevelCalculator.discounts(ConfigManager.get().levelGoal.targetLevel).toArray(ru.wilyfox.client.calculator.LevelCalculator.Discount[]::new),
                        value -> value == ru.wilyfox.client.calculator.LevelCalculator.Discount.MAX ? "Maximum" : value.percent() + "%"));
                items.add(toggle("Goal money remaining", () -> ConfigManager.get().levelGoal.showMoney, value -> ConfigManager.get().levelGoal.showMoney = value));
                items.add(toggle("Goal blocks remaining", () -> ConfigManager.get().levelGoal.showBlocks, value -> ConfigManager.get().levelGoal.showBlocks = value));
                items.add(toggle("Goal progress bar", () -> ConfigManager.get().levelGoal.showBar, value -> ConfigManager.get().levelGoal.showBar = value));
                items.add(toggle("Goal ready notification", () -> ConfigManager.get().levelGoal.notifications, value -> ConfigManager.get().levelGoal.notifications = value));
            }
            case NEARBY_WORMS, NEARBY_BARRELS, NEARBY_CRYSTALS -> {
                java.util.function.Supplier<ru.wilyfox.client.hud.config.NearbyMineWidgetConfig> config = () -> switch (widget) {
                    case NEARBY_WORMS -> ConfigManager.get().nearbyWorms;
                    case NEARBY_BARRELS -> ConfigManager.get().nearbyBarrels;
                    default -> ConfigManager.get().nearbyCrystals;
                };
                items.add(slider("Nearby radius (blocks)", () -> config.get().range, value -> config.get().range = value, 1, 32));
                items.add(toggle("Nearest object distance", () -> config.get().showDistance, value -> config.get().showDistance = value));
                items.add(toggle("Nearest object coordinates", () -> config.get().showCoordinates, value -> config.get().showCoordinates = value));
                items.add(toggle("Show empty nearby counter", () -> config.get().showZero, value -> config.get().showZero = value));
            }
            case IMAGE -> {
                items.add(slider("Image width", () -> ConfigManager.get().imageWidget.width, value -> ConfigManager.get().imageWidget.width = value, 40, 600));
                items.add(slider("Image height", () -> ConfigManager.get().imageWidget.height, value -> ConfigManager.get().imageWidget.height = value, 40, 600));
                items.add(toggle("Keep image aspect ratio", () -> ConfigManager.get().imageWidget.keepAspectRatio, value -> ConfigManager.get().imageWidget.keepAspectRatio = value));
                items.add(toggle("Animate GIF", () -> ConfigManager.get().imageWidget.animateGif, value -> ConfigManager.get().imageWidget.animateGif = value));
                items.add(new ActionSettingsComponent("Clear image", () -> { ConfigManager.get().imageWidget.asset = ""; ConfigManager.save(); }));
            }
            case MINING_STATISTICS -> MiningStatisticsSettings.populate(items);
            case DAILY_CREDITS -> {
                items.add(toggle("Daily credits notifications", () -> ConfigManager.get().dailyCreditsTimer.notifications,
                        value -> ConfigManager.get().dailyCreditsTimer.notifications = value));
                items.add(slider("Credits reminder interval (minutes)", () -> ConfigManager.get().dailyCreditsTimer.reminderMinutes,
                        value -> ConfigManager.get().dailyCreditsTimer.reminderMinutes = value, 1, 1440));
            }
            case ROULETTE -> {
                items.add(toggle("Roulette ready notifications", () -> ConfigManager.get().resourceTimers.notifications,
                        value -> ConfigManager.get().resourceTimers.notifications = value));
                items.add(slider("Reminder interval (minutes)", () -> ConfigManager.get().resourceTimers.reminderMinutes,
                        value -> ConfigManager.get().resourceTimers.reminderMinutes = value, 1, 1440));
            }
            case FPS -> {
                items.add(toggle("Horizontal layout", () -> ConfigManager.get().fpsWidget.horizontal,
                        value -> ConfigManager.get().fpsWidget.horizontal = value));
            }
            case LEVEL_PROGRESS -> {
                items.add(toggle("Show Level Progress Bar", () -> ConfigManager.get().levelProgress.showBar,
                        value -> ConfigManager.get().levelProgress.showBar = value));
            }
            case ESTIMATED_TPS -> {
                toggle(items, "Enable Estimated TPS Monitor", () -> ConfigManager.get().estimatedTps.enabled,
                        value -> ConfigManager.get().estimatedTps.enabled = value);
            }
            case CRAFT_RECIPE -> {
                toggle(items, "Compact Craft Recipe", () -> ConfigManager.get().craftRecipe.compact,
                        value -> ConfigManager.get().craftRecipe.compact = value);
            }
            case BOOSTERS -> {
                toggle(items, "Compact Boosters", () -> ConfigManager.get().boosters.compact,
                        value -> ConfigManager.get().boosters.compact = value);
            }
            case POTION_TIMERS -> {
                items.add(slider("Potion Rows", () -> ConfigManager.get().potionTimers.maxEntries,
                        value -> ConfigManager.get().potionTimers.maxEntries = value, 1, 15));
                items.add(toggle("Potion Icons", () -> ConfigManager.get().potionTimers.showIcons,
                        value -> ConfigManager.get().potionTimers.showIcons = value));
                items.add(slider("Potion Ready Grace (s)", () -> ConfigManager.get().potionTimers.belowZeroSeconds,
                        value -> ConfigManager.get().potionTimers.belowZeroSeconds = value, 0, 60));
            }
            case SELLERS -> {
                items.add(cycle("Seller Filter", () -> ConfigManager.get().sellerCooldown.filter,
                        value -> ConfigManager.get().sellerCooldown.filter = value,
                        SellerCooldownFilter.values(), SellerCooldownFilter::getTitle));
            }
            case COMBO -> {
                items.add(toggle("Show Combo Progress Bar", () -> ConfigManager.get().comboProgress.showBar,
                        value -> ConfigManager.get().comboProgress.showBar = value));
            }
            case WAND -> {
                items.add(toggle("Numeric Cooldown", () -> ConfigManager.get().wandCooldown.numericCooldown,
                        value -> ConfigManager.get().wandCooldown.numericCooldown = value));
            }
            case VISIBILITY -> {
                toggle(items, "Compact Visibility Status", () -> ConfigManager.get().visibilityStatus.compact,
                        value -> ConfigManager.get().visibilityStatus.compact = value);
            }
            case MAP -> {
                toggle(items, "Center Dungeon Map on Player", () -> ConfigManager.get().dungeonMap.anchorDungeonMap,
                        value -> ConfigManager.get().dungeonMap.anchorDungeonMap = value);
                stepper(items, "Dungeon Map Zoom", () -> ConfigManager.get().dungeonMap.dungeonZoomPercent,
                        value -> ConfigManager.get().dungeonMap.dungeonZoomPercent = value, 100, 310, 25);
                toggle(items, "Rotate Dungeon Map", () -> ConfigManager.get().dungeonMap.rotateDungeonMap,
                        value -> ConfigManager.get().dungeonMap.rotateDungeonMap = value);
                stepper(items, "Siege Map Zoom", () -> ConfigManager.get().dungeonMap.siegeZoomPercent,
                        value -> ConfigManager.get().dungeonMap.siegeZoomPercent = value, 100, 310, 25);
                toggle(items, "Rotate Siege Map", () -> ConfigManager.get().dungeonMap.rotateSiegeMap,
                        value -> ConfigManager.get().dungeonMap.rotateSiegeMap = value);
                toggle(items, "Hide Physical Siege Maps", () -> ConfigManager.get().dungeonMap.hidePhysicalSiegeMap,
                        value -> ConfigManager.get().dungeonMap.hidePhysicalSiegeMap = value);
            }
            case BOSS_TIMERS -> {
                items.add(
                        new ToggleSettingsComponent(
                                0, 0, 0, 0,
                                "Keep Spawned Until Killed",
                                () -> ConfigManager.get().bossWidget.showSpawnedUntilKilled,
                                value -> ConfigManager.get().bossWidget.showSpawnedUntilKilled = value
                        )
                );
                items.add(
                        new DragNumberSettingsComponent(
                                0, 0, 0, 0,
                                "Keep After Spawn (s)",
                                () -> ConfigManager.get().bossWidget.postSpawnShowSeconds,
                                value -> ConfigManager.get().bossWidget.postSpawnShowSeconds = value,
                                0, 600
                        ).withVisibility(() -> !ConfigManager.get().bossWidget.showSpawnedUntilKilled)
                );

                items.add(
                        new CycleSettingsComponent<>(
                                0, 0, 0, 0,
                                "Timer source",
                                () -> ConfigManager.get().bossWidget.sourceMode,
                                value -> ConfigManager.get().bossWidget.sourceMode = value,
                                BossTimerSourceMode.values(),
                                BossTimerSourceMode::getTitle
                        )
                );

                items.add(
                        new DragNumberSettingsComponent(
                                0, 0, 0, 0,
                                "Max bosses",
                                () -> ConfigManager.get().bossWidget.maxBosses,
                                value -> ConfigManager.get().bossWidget.maxBosses = value,
                                1, 50, 1
                        )
                );

                items.add(
                        new DragNumberSettingsComponent(
                                0, 0, 0, 0,
                                "Min level",
                                () -> ConfigManager.get().bossWidget.minLevel,
                                value -> ConfigManager.get().bossWidget.minLevel = value,
                                15, WidgetSettingsSections::bossLevelCeiling, 5
                        )
                );

                items.add(
                        new DragNumberSettingsComponent(
                                0, 0, 0, 0,
                                "Max level",
                                () -> ConfigManager.get().bossWidget.maxLevel,
                                value -> ConfigManager.get().bossWidget.maxLevel = value,
                                15, WidgetSettingsSections::bossLevelCeiling, 5
                        ).withVisibility(() -> !ConfigManager.get().bossWidget.matchPlayerLevel)
                );

                items.add(new ToggleSettingsComponent(0, 0, 0, 0, "Match player level",
                        () -> ConfigManager.get().bossWidget.matchPlayerLevel,
                        value -> ConfigManager.get().bossWidget.matchPlayerLevel = value));
                items.add(new StatusSettingsComponent("Auto max level", () -> {
                    int level = ru.wilyfox.client.protocol.DiamondWorldProtocolClient.getCurrentLevel();
                    return level > 0 ? Integer.toString(level) : "Waiting for player level";
                }).withVisibility(() -> ConfigManager.get().bossWidget.matchPlayerLevel));

                items.add(new BossBlacklistSettingsComponent());
                items.add(toggle("Full alignment", () -> ConfigManager.get().bossWidget.fullAligment,
                        value -> ConfigManager.get().bossWidget.fullAligment = value));
                items.add(new BreakLineSettingsComponent("Row builder"));
                items.add(new BossTimerPreviewComponent());
                for (var part : ru.wilyfox.client.hud.config.BossTimerElement.values()) {
                    items.add(toggle(part.title(), () -> part.visible(ConfigManager.get().bossWidget),
                            value -> part.visible(ConfigManager.get().bossWidget, value)));
                    if (part == ru.wilyfox.client.hud.config.BossTimerElement.ICON) continue;
                    items.add(new TextInputSettingsComponent(0, 0, 0, 0, "Color code",
                            () -> ConfigManager.get().bossWidget.colorCode(part),
                            value -> ConfigManager.get().bossWidget.colorCode(part, value), 2) {
                        @Override public String getTooltip(int mouseX, int mouseY) {
                            return isHovered(mouseX, mouseY) ? "&0–&f; empty = theme color" : null;
                        }
                    }.withIndent(12).withVisibility(() -> part.visible(ConfigManager.get().bossWidget)));
                }
            }
            case POP_UPS -> {
                items.add(
                        new DragNumberSettingsComponent(
                                0, 0, 0, 0,
                                "Visible at once",
                                () -> ConfigManager.get().popUps.maxVisible,
                                value -> ConfigManager.get().popUps.maxVisible = value,
                                1, 6, 1
                        )
                );

                items.add(
                        new DragNumberSettingsComponent(
                                0, 0, 0, 0,
                                "Hold ms",
                                () -> ConfigManager.get().popUps.holdMillis,
                                value -> ConfigManager.get().popUps.holdMillis = value,
                                500, 10000, 100
                        )
                );

                items.add(
                        new DragNumberSettingsComponent(
                                0, 0, 0, 0,
                                "Fade-in ms",
                                () -> ConfigManager.get().popUps.fadeInMillis,
                                value -> ConfigManager.get().popUps.fadeInMillis = value,
                                0, 3000, 20
                        )
                );

                items.add(
                        new DragNumberSettingsComponent(
                                0, 0, 0, 0,
                                "Fade-out ms",
                                () -> ConfigManager.get().popUps.fadeOutMillis,
                                value -> ConfigManager.get().popUps.fadeOutMillis = value,
                                0, 3000, 20
                        )
                );
            }
            case FISHING_NIBBLES -> {
                items.add(
                        new CycleSettingsComponent<>(0, 0, 0, 0, "Nibbles visibility",
                                () -> ConfigManager.get().fishing.nibblesVisibility,
                                value -> ConfigManager.get().fishing.nibblesVisibility = value,
                                FishingWidgetVisibility.values(), FishingWidgetVisibility::displayName)
                );

                items.add(
                        new CycleSettingsComponent<>(0, 0, 0, 0, "Nibbles sorting",
                                () -> ConfigManager.get().fishing.nibblesSort,
                                value -> ConfigManager.get().fishing.nibblesSort = value,
                                FishingNibblesSort.values(), FishingNibblesSort::displayName)
                );
            }
            case FISHING_QUESTS -> {
                items.add(
                        new CycleSettingsComponent<>(0, 0, 0, 0, "Quests visibility",
                                () -> ConfigManager.get().fishing.questsVisibility,
                                value -> ConfigManager.get().fishing.questsVisibility = value,
                                FishingWidgetVisibility.values(), FishingWidgetVisibility::displayName)
                );

                items.add(
                        new CycleSettingsComponent<>(0, 0, 0, 0, "Quest dimension",
                                () -> ConfigManager.get().fishing.questsTypeFilter,
                                value -> ConfigManager.get().fishing.questsTypeFilter = value,
                                FishingQuestTypeFilter.values(), FishingQuestTypeFilter::displayName)
                );

                items.add(
                        new CycleSettingsComponent<>(0, 0, 0, 0, "Quest descriptions",
                                () -> ConfigManager.get().fishing.questsDescription,
                                value -> ConfigManager.get().fishing.questsDescription = value,
                                FishingQuestDescriptionMode.values(), FishingQuestDescriptionMode::displayName)
                );
            }
            default -> { }
        }
        return items;
    }
    private static int bossLevelCeiling() {
        return Math.max(BossWidgetConfig.MAX_LEVEL_CEILING, DiamondWorldProtocolClient.getHighestKnownBossLevel());
    }

    private static void toggle(List<SettingsComponent> items, String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        items.add(toggle(label, getter, setter));
    }

    private static ToggleSettingsComponent toggle(String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return new ToggleSettingsComponent(0, 0, 0, 0, label, getter, setter);
    }

    private static DragNumberSettingsComponent slider(String label, IntSupplier getter, IntConsumer setter, int min, int max) {
        return new DragNumberSettingsComponent(0, 0, 0, 0, label, getter, setter, min, max);
    }

    private static void stepper(
            List<SettingsComponent> items,
            String label,
            IntSupplier getter,
            IntConsumer setter,
            int min,
            int max,
            int step
    ) {
        items.add(new DragNumberSettingsComponent(0, 0, 0, 0, label, getter, setter, min, max, step));
    }

    private static <T> CycleSettingsComponent<T> cycle(
            String label,
            Supplier<T> getter,
            Consumer<T> setter,
            T[] values,
            Function<T, String> formatter
    ) {
        return new CycleSettingsComponent<>(0, 0, 0, 0, label, getter, setter, values, formatter);
    }
}
