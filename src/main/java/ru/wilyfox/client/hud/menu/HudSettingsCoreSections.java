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
        sections.get(SettingsCategory.CONFIGS).add(new StatusSettingsComponent("Active config", () ->
                ru.wilyfox.client.hud.config.ConfigProfiles.entries().stream()
                        .filter(entry -> entry.id().equals(ru.wilyfox.client.hud.config.ConfigProfiles.activeId()))
                        .map(ru.wilyfox.client.hud.config.ConfigProfileStore.Entry::name).findFirst().orElse("Main")));
        sections.get(SettingsCategory.CONFIGS).add(new ActionSettingsComponent("Select / delete downloaded configs", () -> {
            var client = net.minecraft.client.Minecraft.getInstance();
            client.gui.setScreen(new ru.wilyfox.client.hud.config.ConfigProfilesScreen(client.gui.screen()));
        }));
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
        items.add(toggle("Show clan in clean names", () -> ConfigManager.get().render.cleanNamesShowClan,
                value -> ConfigManager.get().render.cleanNamesShowClan = value)
                .withVisibility(() -> ConfigManager.get().render.cleanPlayerNames));
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
        toggle(items, "AntiBlind", () -> ConfigManager.get().render.antiBlind,
                value -> ConfigManager.get().render.antiBlind = value);
        toggle(items, "Show server in TAB", () -> ConfigManager.get().render.showCurrentServerInTab,
                value -> ConfigManager.get().render.showCurrentServerInTab = value);

        section(items, "Clear substances");
        toggle(items, "Clear Water", () -> ConfigManager.get().render.clearWater,
                value -> ConfigManager.get().render.clearWater = value);
        toggle(items, "Clear Lava", () -> ConfigManager.get().render.clearLava,
                value -> ConfigManager.get().render.clearLava = value);
        toggle(items, "Clear Powder Snow", () -> ConfigManager.get().render.clearPowderSnow,
                value -> ConfigManager.get().render.clearPowderSnow = value);

        section(items, "Animation");
        toggle(items, "Static hand", () -> ConfigManager.get().render.staticHand,
                value -> ConfigManager.get().render.staticHand = value);

        section(items, "Highlights");
        toggle(items, "Dungeon decorations highlight", () -> ConfigManager.get().render.dungeonDecorationHighlight,
                value -> ConfigManager.get().render.dungeonDecorationHighlight = value);
        items.add(toggle("Useful items highlight", () -> ConfigManager.get().render.usefulItemsHighlight,
                value -> ConfigManager.get().render.usefulItemsHighlight = value).withWarningTooltip("Performance sensitive"));

        section(items, "Utility");
        items.add(new ActionSettingsComponent("Import ChatPlus tabs", () -> {
            var client = net.minecraft.client.Minecraft.getInstance();
            client.gui.setScreen(new ru.wilyfox.client.chat.ChatPlusImportScreen(client.gui.screen()));
        }));
        toggle(items, "AutoSprint", () -> ConfigManager.get().render.autoSprint,
                value -> ConfigManager.get().render.autoSprint = value);
        toggle(items, "Tone-down chat", () -> ConfigManager.get().render.toneDownChat,
                value -> ConfigManager.get().render.toneDownChat = value);
        toggle(items, "Chat timestamps", () -> ConfigManager.get().render.chatTimestamps,
                value -> ConfigManager.get().render.chatTimestamps = value);
        toggle(items, "Collapse repeated messages", () -> ConfigManager.get().render.collapseChatRepeats,
                value -> {
                    ConfigManager.get().render.collapseChatRepeats = value;
                    ru.wilyfox.client.chat.ChatTabManager.getInstance().clearLineCache();
                    ru.wilyfox.client.chat.ChatTabManager.getInstance().rebuildVanillaChat();
                });
        items.add(new CycleSettingsComponent<>(0, 0, 0, 0, "Timestamp format",
                () -> ConfigManager.get().render.timestamp.format, value -> ConfigManager.get().render.timestamp.format = value,
                ru.wilyfox.client.hud.config.ChatTimestampConfig.Format.values(), ru.wilyfox.client.hud.config.ChatTimestampConfig.Format::label)
                .withVisibility(() -> ConfigManager.get().render.chatTimestamps));
        items.add(new CycleSettingsComponent<>(0, 0, 0, 0, "Timestamp brackets",
                () -> ConfigManager.get().render.timestamp.brackets, value -> ConfigManager.get().render.timestamp.brackets = value,
                ru.wilyfox.client.hud.config.ChatTimestampConfig.Brackets.values(), ru.wilyfox.client.hud.config.ChatTimestampConfig.Brackets::label)
                .withVisibility(() -> ConfigManager.get().render.chatTimestamps));
        items.add(toggle("Timestamp theme color", () -> ConfigManager.get().render.timestamp.themeColor,
                value -> ConfigManager.get().render.timestamp.themeColor = value).withVisibility(() -> ConfigManager.get().render.chatTimestamps));
        items.add(new ColorPickerSettingsComponent("Timestamp color",
                () -> ConfigManager.get().render.timestamp.color, value -> ConfigManager.get().render.timestamp.color = value)
                .withVisibility(() -> ConfigManager.get().render.chatTimestamps && !ConfigManager.get().render.timestamp.themeColor));
        items.add(toggle("Timestamp bold", () -> ConfigManager.get().render.timestamp.bold,
                value -> ConfigManager.get().render.timestamp.bold = value).withVisibility(() -> ConfigManager.get().render.chatTimestamps));
        items.add(toggle("Timestamp italic", () -> ConfigManager.get().render.timestamp.italic,
                value -> ConfigManager.get().render.timestamp.italic = value).withVisibility(() -> ConfigManager.get().render.chatTimestamps));
        items.add(new ChatTimestampPreviewSettingsComponent().withVisibility(() -> ConfigManager.get().render.chatTimestamps));
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
        section(items, "Snapping");
        toggle(items, "Widget snapping", () -> ConfigManager.get().snapping.enabled, value -> ConfigManager.get().snapping.enabled = value);
        cycle(items, "Snap behavior", () -> ConfigManager.get().snapping.behavior,
                value -> ConfigManager.get().snapping.behavior = value,
                ru.wilyfox.client.hud.config.HudSnapConfig.Behavior.values(), ru.wilyfox.client.hud.config.HudSnapConfig.Behavior::title);
        slider(items, "Capture distance (screen px)", () -> ConfigManager.get().snapping.capturePixels,
                value -> { ConfigManager.get().snapping.capturePixels = value; ConfigManager.get().snapping.sanitize(); }, 2, 24);
        slider(items, "Release distance (screen px)", () -> ConfigManager.get().snapping.releasePixels,
                value -> { ConfigManager.get().snapping.releasePixels = value; ConfigManager.get().snapping.sanitize(); }, 2, 48);
        slider(items, "New connection gap", () -> ConfigManager.get().snapping.gap, value -> ConfigManager.get().snapping.gap = value, 0, 30);
        toggle(items, "Snap guides", () -> ConfigManager.get().snapping.showGuides, value -> ConfigManager.get().snapping.showGuides = value);
        toggle(items, "Screen / hotbar targets", () -> ConfigManager.get().snapping.screenTargets, value -> ConfigManager.get().snapping.screenTargets = value);
        toggle(items, "Align widget edges", () -> ConfigManager.get().snapping.edgeAlignment, value -> ConfigManager.get().snapping.edgeAlignment = value);
        toggle(items, "Align centers", () -> ConfigManager.get().snapping.centerAlignment, value -> ConfigManager.get().snapping.centerAlignment = value);
        toggle(items, "Equal spacing", () -> ConfigManager.get().snapping.equalSpacing, value -> ConfigManager.get().snapping.equalSpacing = value);
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
