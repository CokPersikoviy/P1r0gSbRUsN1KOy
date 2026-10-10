package ru.wilyfox.client.hud.menu;

import ru.wilyfox.client.hud.config.ConfigManager;
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
        toggle(items, "Fixed chat widget font", () -> ConfigManager.get().render.fixedChatWidgetFont,
                value -> ConfigManager.get().render.fixedChatWidgetFont = value);
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
        items.add(new ActionSettingsComponent("Edit Main Layout", () -> {
            var client = net.minecraft.client.Minecraft.getInstance();
            var renderer = ru.wilyfox.client.Client.getInstance().getHudRenderer();
            client.gui.setScreen(new ru.wilyfox.client.hud.HudEditingScreen(renderer));
            renderer.setSettings(false);
            renderer.setEditing(true);
            renderer.selectLayout("");
        }));
        section(items, "Core");
        toggle(items, "UnClutter (hide widget titles)", () -> ConfigManager.get().render.unclutterWidgets,
                value -> ConfigManager.get().render.unclutterWidgets = value);
        cycle(items, "Background", () -> ConfigManager.get().render.widgetChrome,
                value -> ConfigManager.get().render.widgetChrome = value, WidgetChrome.values(), WidgetChrome::label);
        toggle(items, "Native Renderer (no blur, if glass lags)", () -> ConfigManager.get().render.nativeRenderer,
                value -> ConfigManager.get().render.nativeRenderer = value);
        toggle(items, "Lightweight HUD (no blur / item icons)", () -> ConfigManager.get().render.lightweightHud,
                value -> ConfigManager.get().render.lightweightHud = value);

        slider(items, "BG Opacity", () -> ConfigManager.get().theme.widgetBackgroundOpacityPercent,
                value -> ConfigManager.get().theme.widgetBackgroundOpacityPercent = value, 0, 50);
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
