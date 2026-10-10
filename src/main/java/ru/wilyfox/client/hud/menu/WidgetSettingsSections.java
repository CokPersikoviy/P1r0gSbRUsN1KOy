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
            items.add(new TextInputSettingsComponent(0, 0, 0, 0, "Tab name", () -> config.get().title,
                    value -> config.get().title = value, 20));
            items.add(new TextInputSettingsComponent(0, 0, 0, 0, "Text filter", () -> config.get().textFilter,
                    value -> config.get().textFilter = value, 128));
            items.add(cycle("Channel", () -> config.get().channel, value -> config.get().channel = value,
                    ru.wilyfox.client.chat.ChatTab.values(), ru.wilyfox.client.chat.ChatTab::getTitle));
            items.add(slider("Chat width", () -> config.get().width, value -> config.get().width = value, 100, 600));
            items.add(slider("Chat rows", () -> config.get().rows, value -> config.get().rows = value, 2, 30));
            items.add(toggle("Show chat title", () -> config.get().showTitle, value -> config.get().showTitle = value));
            return items;
        }
        switch (widget) {
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
                        )
                );

                items.add(
                        new ToggleSettingsComponent(
                                0, 0, 0, 0,
                                "Show name",
                                () -> ConfigManager.get().bossWidget.showName,
                                value -> ConfigManager.get().bossWidget.showName = value
                        )
                );

                items.add(
                        new ToggleSettingsComponent(
                                0, 0, 0, 0,
                                "Show icons",
                                () -> ConfigManager.get().bossWidget.showIcons,
                                value -> ConfigManager.get().bossWidget.showIcons = value
                        )
                );

                items.add(
                        new ToggleSettingsComponent(
                                0, 0, 0, 0,
                                "Show level",
                                () -> ConfigManager.get().bossWidget.showLevel,
                                value -> ConfigManager.get().bossWidget.showLevel = value
                        )
                );

                items.add(
                        new ToggleSettingsComponent(
                                0, 0, 0, 0,
                                "Show timer",
                                () -> ConfigManager.get().bossWidget.showTimer,
                                value -> ConfigManager.get().bossWidget.showTimer = value
                        )
                );

                items.add(
                        new BossBlacklistSettingsComponent()
                );

                items.add(
                        new ToggleSettingsComponent(
                                0, 0, 0, 0,
                                "Show collectibles",
                                () -> ConfigManager.get().bossWidget.showCollectibles,
                                value -> ConfigManager.get().bossWidget.showCollectibles = value
                        )
                );

                items.add(
                        new ToggleSettingsComponent(
                                0, 0, 0, 0,
                                "Full alignment",
                                () -> ConfigManager.get().bossWidget.fullAligment,
                                value -> ConfigManager.get().bossWidget.fullAligment = value
                        )
                );
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
