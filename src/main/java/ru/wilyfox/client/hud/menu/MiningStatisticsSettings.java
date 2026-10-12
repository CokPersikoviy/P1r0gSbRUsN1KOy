package ru.wilyfox.client.hud.menu;

import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.MiningStatisticsConfig;
import ru.wilyfox.client.statistic.MiningStatisticsTracker;
import java.util.List;
import java.util.function.*;

final class MiningStatisticsSettings {
    private MiningStatisticsSettings() {}
    private static MiningStatisticsConfig c() { return ConfigManager.get().miningStatistics; }
    static void populate(List<SettingsComponent> items) {
        items.add(new ActionSettingsComponent("Mining pause / resume", MiningStatisticsTracker::togglePause));
        items.add(new ActionSettingsComponent("Reset mining session", MiningStatisticsTracker::reset));
        items.add(new StatusSettingsComponent("Mining session state", () -> MiningStatisticsTracker.STORE.snapshot(c()).state().name()));
        items.add(toggle("Mining auto pause", () -> c().autoPause, v -> c().autoPause = v));
        items.add(number("Mining idle timeout (s)", () -> c().idleSeconds, v -> c().idleSeconds = v, 1, 300).withVisibility(() -> c().autoPause));
        items.add(toggle("Track mining money (autosell)", () -> c().trackMoney, v -> c().trackMoney = v)
                .withWarningTooltip("Income is estimated from server chat and action bar. Disable without autosell."));
        items.add(new KeybindSettingsComponent(0, 0, 0, 0, "Mining pause key", () -> c().pauseKey, v -> c().pauseKey = v)
                .withModifiers(() -> c().pauseModifiers, v -> c().pauseModifiers = v));
        items.add(new KeybindSettingsComponent(0, 0, 0, 0, "Mining reset key", () -> c().resetKey, v -> c().resetKey = v)
                .withModifiers(() -> c().resetModifiers, v -> c().resetModifiers = v));
        for (var source : MiningStatisticsConfig.Source.values()) {
            items.add(toggle("Mining income: " + title(source), () -> c().sources.contains(source), v -> {
                if (v) c().sources.add(source); else c().sources.remove(source);
            }).withVisibility(() -> c().trackMoney));
        }
        items.add(new CycleSettingsComponent<>(0, 0, 0, 0, "Mining layout", () -> c().layout, v -> c().layout = v,
                MiningStatisticsConfig.Layout.values(), MiningStatisticsSettings::title));
        items.add(new TextInputSettingsComponent(0, 0, 0, 0, "Mining title", () -> c().title, v -> c().title = v, 48));
        items.add(toggle("Mining show title", () -> c().showTitle, v -> c().showTitle = v));
        items.add(toggle("Mining show time", () -> c().showTime, v -> c().showTime = v));
        items.add(toggle("Mining show status", () -> c().showStatus, v -> c().showStatus = v));
        items.add(toggle("Mining show blocks", () -> c().showBlocks, v -> c().showBlocks = v));
        items.add(toggle("Mining show money", () -> c().showMoney, v -> c().showMoney = v));
        items.add(toggle("Mining show shards", () -> c().showShards, v -> c().showShards = v));
        items.add(toggle("Mining show totals", () -> c().showTotals, v -> c().showTotals = v));
        items.add(toggle("Mining show per hour", () -> c().showRates, v -> c().showRates = v));
        items.add(toggle("Mining show labels", () -> c().showLabels, v -> c().showLabels = v));
        items.add(toggle("Mining show icons", () -> c().showIcons, v -> c().showIcons = v));
        items.add(toggle("Mining show column headers", () -> c().showHeaders, v -> c().showHeaders = v)
                .withVisibility(() -> c().layout == MiningStatisticsConfig.Layout.TABLE));
        items.add(new CycleSettingsComponent<>(0, 0, 0, 0, "Mining number format", () -> c().numberFormat, v -> c().numberFormat = v,
                MiningStatisticsConfig.NumberFormat.values(), MiningStatisticsSettings::title));
        items.add(number("Mining decimals", () -> c().decimals, v -> c().decimals = v, 0, 2));
        items.add(number("Mining row spacing", () -> c().rowGap, v -> c().rowGap = v, 0, 10));
        items.add(number("Mining minimum width", () -> c().minimumWidth, v -> c().minimumWidth = v, 60, 500));
        items.add(toggle("Mining custom colors", () -> c().customColors, v -> c().customColors = v));
        color(items, "Mining title color", () -> c().titleColor, v -> c().titleColor = v);
        color(items, "Mining label color", () -> c().labelColor, v -> c().labelColor = v);
        color(items, "Mining blocks color", () -> c().blocksColor, v -> c().blocksColor = v);
        color(items, "Mining money color", () -> c().moneyColor, v -> c().moneyColor = v);
        color(items, "Mining shards color", () -> c().shardsColor, v -> c().shardsColor = v);
        color(items, "Mining time color", () -> c().timeColor, v -> c().timeColor = v);
        color(items, "Mining running color", () -> c().runningColor, v -> c().runningColor = v);
        color(items, "Mining paused color", () -> c().pausedColor, v -> c().pausedColor = v);
    }
    private static ToggleSettingsComponent toggle(String label, Supplier<Boolean> get, Consumer<Boolean> set) {
        return new ToggleSettingsComponent(0, 0, 0, 0, label, get, set);
    }
    private static SettingsComponent number(String label, IntSupplier get, IntConsumer set, int min, int max) {
        return new DragNumberSettingsComponent(0, 0, 0, 0, label, get, set, min, max);
    }
    private static void color(List<SettingsComponent> items, String label, IntSupplier get, IntConsumer set) {
        items.add(new ColorPickerSettingsComponent(label, get, set).withAlpha().withVisibility(() -> c().customColors));
    }
    private static String title(Enum<?> value) {
        String text = value.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
