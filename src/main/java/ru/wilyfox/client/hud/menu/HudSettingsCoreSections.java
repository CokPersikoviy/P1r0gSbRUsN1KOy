package ru.wilyfox.client.hud.menu;

import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.SellerCooldownFilter;
import ru.wilyfox.client.hud.config.WidgetChrome;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class HudSettingsCoreSections {
    private HudSettingsCoreSections() {
    }

    static void populate(Map<SettingsCategory, List<SettingsComponent>> sections) {
        populateRender(sections.get(SettingsCategory.RENDER));
        populateWidgets(sections.get(SettingsCategory.WIDGET));
        HudSettingsVisualsSection.populate(sections.get(SettingsCategory.VISUALS));
    }

    private static void populateRender(List<SettingsComponent> items) {
        section(items, "Visual");
        toggle(items, "Debug", () -> ConfigManager.get().render.debug, value -> ConfigManager.get().render.debug = value);
        toggle(items, "Hide Cosmetics (First Person)", () -> ConfigManager.get().render.hideFirstPersonCosmetics,
                value -> ConfigManager.get().render.hideFirstPersonCosmetics = value);
        toggle(items, "Clean player names (TAB / nametags)", () -> ConfigManager.get().render.cleanPlayerNames,
                value -> ConfigManager.get().render.cleanPlayerNames = value);
        toggle(items, "FrogHelper Badge On Nametags", () -> ConfigManager.get().render.modUserBadge,
                value -> ConfigManager.get().render.modUserBadge = value);
        toggle(items, "Hide block particles", () -> ConfigManager.get().render.hideBlockBreakParticles,
                value -> ConfigManager.get().render.hideBlockBreakParticles = value);
        toggle(items, "Hide lightning", () -> ConfigManager.get().render.hideLightningEffect,
                value -> ConfigManager.get().render.hideLightningEffect = value);
        toggle(items, "Hide hurt shake", () -> ConfigManager.get().render.hideHurtCameraShake,
                value -> ConfigManager.get().render.hideHurtCameraShake = value);
        toggle(items, "Hide fire overlay", () -> ConfigManager.get().render.hideFireOverlay,
                value -> ConfigManager.get().render.hideFireOverlay = value);
        toggle(items, "Show server in TAB", () -> ConfigManager.get().render.showCurrentServerInTab,
                value -> ConfigManager.get().render.showCurrentServerInTab = value);

        section(items, "Animation");
        toggle(items, "Static hand", () -> ConfigManager.get().render.staticHand,
                value -> ConfigManager.get().render.staticHand = value);

        section(items, "Highlights");
        toggle(items, "Dungeon decorations highlight", () -> ConfigManager.get().render.dungeonDecorationHighlight,
                value -> ConfigManager.get().render.dungeonDecorationHighlight = value);
        items.add(toggle("Useful items highlight", () -> ConfigManager.get().render.usefulItemsHighlight,
                value -> ConfigManager.get().render.usefulItemsHighlight = value).withWarningTooltip("Performance sensitive"));

        section(items, "Utility");
        toggle(items, "Tone-down chat", () -> ConfigManager.get().render.toneDownChat,
                value -> ConfigManager.get().render.toneDownChat = value);
        toggle(items, "Chat timestamps", () -> ConfigManager.get().render.chatTimestamps,
                value -> ConfigManager.get().render.chatTimestamps = value);
        toggle(items, "Copy chat by RMB", () -> ConfigManager.get().render.copyChatMessages,
                value -> ConfigManager.get().render.copyChatMessages = value);
        items.add(toggle("Full Message Copy", () -> ConfigManager.get().render.fullMessageCopy,
                value -> ConfigManager.get().render.fullMessageCopy = value)
                .withVisibility(() -> ConfigManager.get().render.copyChatMessages));
        stepper(items, "Extra chat history", () -> ConfigManager.get().render.extraChatHistoryLines,
                value -> ConfigManager.get().render.extraChatHistoryLines = value, 0, 10_000, 50);
        toggle(items, "Auto thx", () -> ConfigManager.get().render.autoThanks,
                value -> ConfigManager.get().render.autoThanks = value);
    }

    private static void populateWidgets(List<SettingsComponent> items) {
        section(items, "Core");
        toggle(items, "UnClutter (hide widget titles)", () -> ConfigManager.get().render.unclutterWidgets,
                value -> ConfigManager.get().render.unclutterWidgets = value);
        cycle(items, "Background", () -> ConfigManager.get().render.widgetChrome,
                value -> ConfigManager.get().render.widgetChrome = value, WidgetChrome.values(), WidgetChrome::label);
        toggle(items, "Native Renderer (no blur, if glass lags)", () -> ConfigManager.get().render.nativeRenderer,
                value -> ConfigManager.get().render.nativeRenderer = value);
        toggle(items, "Lightweight HUD (no blur / item icons)", () -> ConfigManager.get().render.lightweightHud,
                value -> ConfigManager.get().render.lightweightHud = value);
        toggle(items, "Render BossBar as Widget", () -> ConfigManager.get().bossBar.active,
                value -> ConfigManager.get().bossBar.active = value);
        toggle(items, "Render Scoreboard as Widget", () -> ConfigManager.get().scoreboard.active,
                value -> ConfigManager.get().scoreboard.active = value);
        toggle(items, "Show Chat Queue Widget", () -> ConfigManager.get().outgoingChatQueue.active,
                value -> ConfigManager.get().outgoingChatQueue.active = value);
        toggle(items, "Show Protocol Graph Widget", () -> ConfigManager.get().protocolGraphWidget.active,
                value -> ConfigManager.get().protocolGraphWidget.active = value);
        toggle(items, "Show Level Progress Widget", () -> ConfigManager.get().levelProgress.active,
                value -> ConfigManager.get().levelProgress.active = value);
        items.add(toggle("Show Level Progress Bar", () -> ConfigManager.get().levelProgress.showBar,
                value -> ConfigManager.get().levelProgress.showBar = value)
                .withVisibility(() -> ConfigManager.get().levelProgress.active));
        toggle(items, "Show Block Counter Widget", () -> ConfigManager.get().blocksPerSecondWidget.active,
                value -> ConfigManager.get().blocksPerSecondWidget.active = value);
        toggle(items, "Show Daily Blocks Widget", () -> ConfigManager.get().dailyBlocks.active,
                value -> ConfigManager.get().dailyBlocks.active = value);
        toggle(items, "Enable Estimated TPS Monitor", () -> ConfigManager.get().estimatedTps.enabled,
                value -> ConfigManager.get().estimatedTps.enabled = value);
        toggle(items, "Show Estimated TPS Widget", () -> ConfigManager.get().estimatedTps.active,
                value -> ConfigManager.get().estimatedTps.active = value);

        section(items, "Recipes");
        toggle(items, "Show Craft Recipe Widget", () -> ConfigManager.get().craftRecipe.active,
                value -> ConfigManager.get().craftRecipe.active = value);
        toggle(items, "Compact Craft Recipe", () -> ConfigManager.get().craftRecipe.compact,
                value -> ConfigManager.get().craftRecipe.compact = value);

        section(items, "Status");
        toggle(items, "Show Boosters Widget", () -> ConfigManager.get().boosters.active,
                value -> ConfigManager.get().boosters.active = value);
        toggle(items, "Compact Boosters", () -> ConfigManager.get().boosters.compact,
                value -> ConfigManager.get().boosters.compact = value);
        slider(items, "BG Opacity", () -> ConfigManager.get().theme.widgetBackgroundOpacityPercent,
                value -> ConfigManager.get().theme.widgetBackgroundOpacityPercent = value, 0, 50);

        section(items, "Visibility");
        toggle(items, "Show Potion Cooldowns", () -> ConfigManager.get().potionTimers.active,
                value -> ConfigManager.get().potionTimers.active = value);
        items.add(slider("Potion Rows", () -> ConfigManager.get().potionTimers.maxEntries,
                value -> ConfigManager.get().potionTimers.maxEntries = value, 1, 15)
                .withVisibility(() -> ConfigManager.get().potionTimers.active));
        items.add(toggle("Potion Icons", () -> ConfigManager.get().potionTimers.showIcons,
                value -> ConfigManager.get().potionTimers.showIcons = value)
                .withVisibility(() -> ConfigManager.get().potionTimers.active));
        items.add(slider("Potion Ready Grace (s)", () -> ConfigManager.get().potionTimers.belowZeroSeconds,
                value -> ConfigManager.get().potionTimers.belowZeroSeconds = value, 0, 60)
                .withVisibility(() -> ConfigManager.get().potionTimers.active));
        toggle(items, "Show Seller Cooldowns", () -> ConfigManager.get().sellerCooldown.active,
                value -> ConfigManager.get().sellerCooldown.active = value);
        items.add(cycle("Seller Filter", () -> ConfigManager.get().sellerCooldown.filter,
                value -> ConfigManager.get().sellerCooldown.filter = value,
                SellerCooldownFilter.values(), SellerCooldownFilter::getTitle)
                .withVisibility(() -> ConfigManager.get().sellerCooldown.active));
        toggle(items, "Show Combo Progress", () -> ConfigManager.get().comboProgress.active,
                value -> ConfigManager.get().comboProgress.active = value);
        items.add(toggle("Show Combo Progress Bar", () -> ConfigManager.get().comboProgress.showBar,
                value -> ConfigManager.get().comboProgress.showBar = value)
                .withVisibility(() -> ConfigManager.get().comboProgress.active));
        toggle(items, "Show Wand Cooldown Widget", () -> ConfigManager.get().wandCooldown.active,
                value -> ConfigManager.get().wandCooldown.active = value);
        items.add(toggle("Numeric Cooldown", () -> ConfigManager.get().wandCooldown.numericCooldown,
                value -> ConfigManager.get().wandCooldown.numericCooldown = value)
                .withVisibility(() -> ConfigManager.get().wandCooldown.active));
        toggle(items, "Show Ability Cooldown Widget", () -> ConfigManager.get().abilityCooldown.active,
                value -> ConfigManager.get().abilityCooldown.active = value);
        toggle(items, "Show Active Effects Widget", () -> ConfigManager.get().activeEffects.active,
                value -> ConfigManager.get().activeEffects.active = value);
        toggle(items, "Show Active Runes Widget", () -> ConfigManager.get().activeRunes.active,
                value -> ConfigManager.get().activeRunes.active = value);
        toggle(items, "Show Active Pets Widget", () -> ConfigManager.get().activePets.active,
                value -> ConfigManager.get().activePets.active = value);
        toggle(items, "Show Miners Widget", () -> ConfigManager.get().activeMiners.active,
                value -> ConfigManager.get().activeMiners.active = value);
        toggle(items, "Show Boss Damage Widget", () -> ConfigManager.get().bossDamage.active,
                value -> ConfigManager.get().bossDamage.active = value);
        toggle(items, "Show Visibility Status Widget", () -> ConfigManager.get().visibilityStatus.active,
                value -> ConfigManager.get().visibilityStatus.active = value);
        toggle(items, "Compact Visibility Status", () -> ConfigManager.get().visibilityStatus.compact,
                value -> ConfigManager.get().visibilityStatus.compact = value);

        section(items, "Maps & Inspect");
        toggle(items, "Show Dungeon / Siege Map Widget", () -> ConfigManager.get().dungeonMap.active,
                value -> ConfigManager.get().dungeonMap.active = value);
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
        toggle(items, "Show Entity Inspect Widget", () -> ConfigManager.get().entityInspect.active,
                value -> ConfigManager.get().entityInspect.active = value);
    }

    private static void section(List<SettingsComponent> items, String title) {
        items.add(new BreakLineSettingsComponent(title));
    }

    private static void toggle(List<SettingsComponent> items, String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        items.add(toggle(label, getter, setter));
    }

    private static ToggleSettingsComponent toggle(String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return new ToggleSettingsComponent(0, 0, 0, 0, label, getter, setter);
    }

    private static void slider(List<SettingsComponent> items, String label, IntSupplier getter, IntConsumer setter, int min, int max) {
        items.add(slider(label, getter, setter, min, max));
    }

    private static SliderSettingsComponent slider(String label, IntSupplier getter, IntConsumer setter, int min, int max) {
        return new SliderSettingsComponent(0, 0, 0, 0, label, getter, setter, min, max);
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
        items.add(new StepperSettingsComponent(0, 0, 0, 0, label, getter, setter, min, max, step));
    }

    private static <T> void cycle(
            List<SettingsComponent> items,
            String label,
            Supplier<T> getter,
            Consumer<T> setter,
            T[] values,
            Function<T, String> formatter
    ) {
        items.add(cycle(label, getter, setter, values, formatter));
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
