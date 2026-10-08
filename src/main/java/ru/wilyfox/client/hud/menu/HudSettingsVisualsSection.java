package ru.wilyfox.client.hud.menu;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import ru.wilyfox.client.hud.config.AspectRatioPreset;
import ru.wilyfox.client.hud.config.ConfigManager;

final class HudSettingsVisualsSection {
    private HudSettingsVisualsSection() {}
    static void populate(List<SettingsComponent> items) {
        items.add(new BreakLineSettingsComponent("Screen"));
        items.add(new CycleSettingsComponent<>(0, 0, 0, 0, "Aspect ratio", () -> ConfigManager.get().visuals.aspectRatio,
                value -> ConfigManager.get().visuals.aspectRatio = value, AspectRatioPreset.values(), value -> value.title));
        items.add(new StepperSettingsComponent(0, 0, 0, 0, "Custom width", () -> ConfigManager.get().visuals.aspectWidth,
                value -> ConfigManager.get().visuals.aspectWidth = value, 1, 8192, 10)
                .withVisibility(() -> ConfigManager.get().visuals.aspectRatio == AspectRatioPreset.CUSTOM));
        items.add(new StepperSettingsComponent(0, 0, 0, 0, "Custom height", () -> ConfigManager.get().visuals.aspectHeight,
                value -> ConfigManager.get().visuals.aspectHeight = value, 1, 8192, 10)
                .withVisibility(() -> ConfigManager.get().visuals.aspectRatio == AspectRatioPreset.CUSTOM));
        items.add(toggle("Stretch hand too", () -> ConfigManager.get().visuals.stretchHand,
                value -> ConfigManager.get().visuals.stretchHand = value)
                .withVisibility(() -> ConfigManager.get().visuals.aspectRatio != AspectRatioPreset.OFF));
        items.add(new BreakLineSettingsComponent("Block outline"));
        items.add(toggle("Custom block outline", () -> ConfigManager.get().visuals.blockOutline,
                value -> ConfigManager.get().visuals.blockOutline = value));
        items.add(new ColorPickerSettingsComponent("Outline color", () -> ConfigManager.get().visuals.blockOutlineColor,
                value -> ConfigManager.get().visuals.blockOutlineColor = value)
                .withVisibility(() -> ConfigManager.get().visuals.blockOutline));
        items.add(new SliderSettingsComponent(0, 0, 0, 0, "Line width (px)", () -> ConfigManager.get().visuals.blockOutlineWidth,
                value -> ConfigManager.get().visuals.blockOutlineWidth = value, 1, 8)
                .withVisibility(() -> ConfigManager.get().visuals.blockOutline));
        items.add(new BreakLineSettingsComponent("Sky / fog"));
        items.add(toggle("Custom sky color", () -> ConfigManager.get().visuals.skyColorEnabled,
                value -> ConfigManager.get().visuals.skyColorEnabled = value));
        items.add(new ColorPickerSettingsComponent("Sky color", () -> ConfigManager.get().visuals.skyColor,
                value -> ConfigManager.get().visuals.skyColor = value)
                .withVisibility(() -> ConfigManager.get().visuals.skyColorEnabled));
        items.add(toggle("Custom fog color", () -> ConfigManager.get().visuals.fogColorEnabled,
                value -> ConfigManager.get().visuals.fogColorEnabled = value));
        items.add(new ColorPickerSettingsComponent("Fog color", () -> ConfigManager.get().visuals.fogColor,
                value -> ConfigManager.get().visuals.fogColor = value)
                .withVisibility(() -> ConfigManager.get().visuals.fogColorEnabled));
    }
    private static ToggleSettingsComponent toggle(String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return new ToggleSettingsComponent(0, 0, 0, 0, label, getter, setter);
    }
}
