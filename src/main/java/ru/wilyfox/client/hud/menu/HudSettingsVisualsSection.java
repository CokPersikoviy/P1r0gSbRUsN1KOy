package ru.wilyfox.client.hud.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.*;
import ru.wilyfox.client.hud.config.*;

final class HudSettingsVisualsSection {
    private enum Section { SCREEN, CROSSHAIR, HITBOXES, BLOCK_OUTLINE, HAND, CAMERA, WORLD, TAB }
    private static Section selected = Section.SCREEN;
    private HudSettingsVisualsSection() {}
    private static VisualsConfig c() { return ConfigManager.get().visuals; }
    static void populate(List<SettingsComponent> items) {
        items.add(new CycleSettingsComponent<>(0, 0, 0, 0, "Section", () -> selected, value -> selected = value, Section.values(), HudSettingsVisualsSection::title));
        for (Section section : Section.values()) {
            List<SettingsComponent> rows = new ArrayList<>();
            switch (section) {
                case SCREEN -> screen(rows);
                case CROSSHAIR -> crosshair(rows);
                case HITBOXES -> hitboxes(rows);
                case BLOCK_OUTLINE -> outline(rows);
                case HAND -> hand(rows);
                case CAMERA -> camera(rows);
                case WORLD -> world(rows);
                case TAB -> tab(rows);
            }
            rows.add(0, new VisualsPreviewComponent(VisualsPreviewComponent.Kind.valueOf(section.name())));
            for (SettingsComponent row : rows) {
                var original = row.visibleWhen;
                items.add(row.withVisibility(() -> selected == section && original.getAsBoolean()));
            }
        }
    }
    private static void screen(List<SettingsComponent> r) {
        r.add(cycle("Aspect ratio", () -> c().aspectRatio, v -> c().aspectRatio = v, AspectRatioPreset.values()));
        r.add(number("Custom width", () -> c().aspectWidth, v -> c().aspectWidth = v, 1, 8192).withVisibility(() -> c().aspectRatio == AspectRatioPreset.CUSTOM));
        r.add(number("Custom height", () -> c().aspectHeight, v -> c().aspectHeight = v, 1, 8192).withVisibility(() -> c().aspectRatio == AspectRatioPreset.CUSTOM));
        r.add(toggle("Stretch hand too", () -> c().stretchHand, v -> c().stretchHand = v).withVisibility(() -> c().aspectRatio != AspectRatioPreset.OFF));
        r.add(toggle("Compact resource reload", () -> c().compactReload, v -> c().compactReload = v));
    }
    private static void crosshair(List<SettingsComponent> r) {
        r.add(toggle("Custom crosshair", () -> c().crosshair.enabled, v -> c().crosshair.enabled = v));
        int start = r.size();
        r.add(cycle("Shape", () -> c().crosshair.style, v -> c().crosshair.style = v, VisualsConfig.CrosshairStyle.values()));
        r.add(cycle("Pixel scale", () -> c().crosshair.scale, v -> c().crosshair.scale = v, VisualsConfig.PixelScale.values()));
        r.add(number("Reference height", () -> c().crosshair.referenceHeight, v -> c().crosshair.referenceHeight = v, 240, 8192).withVisibility(() -> c().crosshair.scale == VisualsConfig.PixelScale.HEIGHT));
        r.add(color("Color", () -> c().crosshair.color, v -> c().crosshair.color = v));
        r.add(toggle("Invert background", () -> c().crosshair.invert, v -> c().crosshair.invert = v));
        r.add(toggle("Color on target", () -> c().crosshair.targetColorEnabled, v -> c().crosshair.targetColorEnabled = v));
        r.add(color("Target color", () -> c().crosshair.targetColor, v -> c().crosshair.targetColor = v).withVisibility(() -> c().crosshair.targetColorEnabled));
        r.add(toggle("Show in third person", () -> c().crosshair.thirdPerson, v -> c().crosshair.thirdPerson = v));
        r.add(decimal("Length (px)", () -> c().crosshair.length, v -> c().crosshair.length = v, 0, 64));
        r.add(toggle("Separate vertical length", () -> c().crosshair.splitLength, v -> c().crosshair.splitLength = v));
        r.add(decimal("Vertical length (px)", () -> c().crosshair.verticalLength, v -> c().crosshair.verticalLength = v, 0, 64).withVisibility(() -> c().crosshair.splitLength));
        r.add(decimal("Thickness (px)", () -> c().crosshair.thickness, v -> c().crosshair.thickness = v, 1, 16));
        r.add(decimal("Gap (px)", () -> c().crosshair.gap, v -> c().crosshair.gap = v, 0, 64));
        r.add(toggle("Top arm", () -> c().crosshair.top, v -> c().crosshair.top = v));
        r.add(toggle("Bottom arm", () -> c().crosshair.bottom, v -> c().crosshair.bottom = v));
        r.add(toggle("Left arm", () -> c().crosshair.left, v -> c().crosshair.left = v));
        r.add(toggle("Right arm", () -> c().crosshair.right, v -> c().crosshair.right = v));
        r.add(toggle("Center dot", () -> c().crosshair.dot, v -> c().crosshair.dot = v));
        r.add(decimal("Dot size (px)", () -> c().crosshair.dotSize, v -> c().crosshair.dotSize = v, 1, 32));
        r.add(toggle("Separate dot color", () -> c().crosshair.dotColorEnabled, v -> c().crosshair.dotColorEnabled = v));
        r.add(color("Dot color", () -> c().crosshair.dotColor, v -> c().crosshair.dotColor = v).withVisibility(() -> c().crosshair.dotColorEnabled));
        r.add(toggle("Outline", () -> c().crosshair.outline, v -> c().crosshair.outline = v));
        r.add(decimal("Outline thickness", () -> c().crosshair.outlineThickness, v -> c().crosshair.outlineThickness = v, 1, 8).withVisibility(() -> c().crosshair.outline));
        r.add(color("Outline color", () -> c().crosshair.outlineColor, v -> c().crosshair.outlineColor = v).withVisibility(() -> c().crosshair.outline));
        r.add(new BreakLineSettingsComponent("Dynamic spread"));
        r.add(toggle("Dynamic crosshair", () -> c().crosshair.dynamic, v -> c().crosshair.dynamic = v));
        r.add(decimal("Run spread", () -> c().crosshair.runSpread, v -> c().crosshair.runSpread = v, 0, 64).withVisibility(() -> c().crosshair.dynamic));
        r.add(decimal("Jump spread", () -> c().crosshair.jumpSpread, v -> c().crosshair.jumpSpread = v, 0, 64).withVisibility(() -> c().crosshair.dynamic));
        r.add(decimal("Attack spread", () -> c().crosshair.attackSpread, v -> c().crosshair.attackSpread = v, 0, 64).withVisibility(() -> c().crosshair.dynamic));
        r.add(decimal("Recovery speed", () -> c().crosshair.recovery, v -> c().crosshair.recovery = v, 1, 40).withVisibility(() -> c().crosshair.dynamic));
        r.add(new BreakLineSettingsComponent("Bow / crossbow / trident"));
        r.add(toggle("Draw feedback", () -> c().crosshair.drawEnabled, v -> c().crosshair.drawEnabled = v));
        int draw = r.size();
        r.add(toggle("Change gap while drawing", () -> c().crosshair.drawGap, v -> c().crosshair.drawGap = v));
        r.add(decimal("Draw gap from", () -> c().crosshair.drawGapFrom, v -> c().crosshair.drawGapFrom = v, -64, 64));
        r.add(decimal("Draw gap to", () -> c().crosshair.drawGapTo, v -> c().crosshair.drawGapTo = v, -64, 64));
        r.add(cycle("Draw color mode", () -> c().crosshair.drawColor, v -> c().crosshair.drawColor = v, VisualsConfig.DrawColor.values()));
        r.add(color("Full draw color", () -> c().crosshair.fullColor, v -> c().crosshair.fullColor = v));
        r.add(toggle("Flash when ready", () -> c().crosshair.drawFlash, v -> c().crosshair.drawFlash = v));
        r.add(cycle("Draw indicator", () -> c().crosshair.indicator, v -> c().crosshair.indicator = v, VisualsConfig.Indicator.values()));
        r.add(decimal("Indicator size", () -> c().crosshair.indicatorSize, v -> c().crosshair.indicatorSize = v, 1, 64));
        r.add(decimal("Indicator thickness", () -> c().crosshair.indicatorThickness, v -> c().crosshair.indicatorThickness = v, 1, 16));
        r.add(color("Indicator color", () -> c().crosshair.indicatorColor, v -> c().crosshair.indicatorColor = v));
        visible(r, draw, () -> c().crosshair.drawEnabled); visible(r, start, () -> c().crosshair.enabled);
    }
    private static void hitboxes(List<SettingsComponent> r) {
        r.add(toggle("Show hitboxes", () -> c().hitboxes.show, v -> c().hitboxes.show = v));
        r.add(bind("Hitbox key", () -> c().hitboxes.key, v -> c().hitboxes.key = v, () -> c().hitboxes.modifiers, v -> c().hitboxes.modifiers = v));
        r.add(toggle("Override F3+B style", () -> c().hitboxes.custom, v -> c().hitboxes.custom = v));
        r.add(cycle("Entities", () -> c().hitboxes.targets, v -> c().hitboxes.targets = v, VisualsConfig.Targets.values()));
        r.add(cycle("Line style", () -> c().hitboxes.style, v -> c().hitboxes.style = v, VisualsConfig.LineStyle.values()));
        r.add(decimal("Line width (px)", () -> c().hitboxes.width, v -> c().hitboxes.width = v, 1, 8));
        r.add(color("Line color", () -> c().hitboxes.color, v -> c().hitboxes.color = v));
        r.add(toggle("Own hitbox in third person", () -> c().hitboxes.self, v -> c().hitboxes.self = v));
        r.add(toggle("Eye level", () -> c().hitboxes.eyeLine, v -> c().hitboxes.eyeLine = v));
        r.add(color("Eye level color", () -> c().hitboxes.eyeColor, v -> c().hitboxes.eyeColor = v).withVisibility(() -> c().hitboxes.eyeLine));
        r.add(toggle("Look arrow", () -> c().hitboxes.lookArrow, v -> c().hitboxes.lookArrow = v));
        r.add(color("Look arrow color", () -> c().hitboxes.lookColor, v -> c().hitboxes.lookColor = v).withVisibility(() -> c().hitboxes.lookArrow));
        r.add(toggle("Highlight target", () -> c().hitboxes.hover, v -> c().hitboxes.hover = v));
        r.add(color("Target color", () -> c().hitboxes.hoverColor, v -> c().hitboxes.hoverColor = v).withVisibility(() -> c().hitboxes.hover));
        r.add(toggle("Highlight hurt entities", () -> c().hitboxes.hit, v -> c().hitboxes.hit = v));
        r.add(color("Hurt color", () -> c().hitboxes.hitColor, v -> c().hitboxes.hitColor = v).withVisibility(() -> c().hitboxes.hit));
        r.add(toggle("Fill", () -> c().hitboxes.fill, v -> c().hitboxes.fill = v));
        r.add(color("Fill color / alpha", () -> c().hitboxes.fillColor, v -> c().hitboxes.fillColor = v).withVisibility(() -> c().hitboxes.fill));
    }
    private static void outline(List<SettingsComponent> r) {
        r.add(toggle("Custom block outline", () -> c().blockOutline, v -> c().blockOutline = v)); int start = r.size();
        r.add(cycle("Shape", () -> c().blockMode, v -> c().blockMode = v, VisualsConfig.OutlineMode.values()));
        r.add(cycle("Line style", () -> c().blockStyle, v -> c().blockStyle = v, VisualsConfig.LineStyle.values()));
        r.add(color("Outline color", () -> c().blockOutlineColor, v -> c().blockOutlineColor = v));
        r.add(decimal("Line width (px)", () -> c().blockOutlineWidth, v -> c().blockOutlineWidth = v, 1, 8));
        r.add(toggle("Fill", () -> c().blockFill, v -> c().blockFill = v));
        r.add(color("Fill color / alpha", () -> c().blockFillColor, v -> c().blockFillColor = v).withVisibility(() -> c().blockFill));
        r.add(toggle("Through walls", () -> c().blockThroughWalls, v -> c().blockThroughWalls = v));
        r.add(number("Move animation (ms)", () -> c().blockAnimation, v -> c().blockAnimation = v, 0, 1000));
        r.add(number("Linger (ms)", () -> c().blockLinger, v -> c().blockLinger = v, 0, 2000));
        r.add(number("Fade in / out (ms)", () -> c().blockFade, v -> c().blockFade = v, 0, 1000));
        r.add(toggle("Rainbow", () -> c().blockRainbow, v -> c().blockRainbow = v));
        r.add(number("Rainbow speed", () -> c().blockRainbowSpeed, v -> c().blockRainbowSpeed = v, 1, 20).withVisibility(() -> c().blockRainbow));
        visible(r, start, () -> c().blockOutline);
    }
    private static void hand(List<SettingsComponent> r) {
        r.add(toggle("Custom view model", () -> c().hand.enabled, v -> c().hand.enabled = v)); int start = r.size();
        r.add(toggle("Mirror main hand", () -> c().hand.mirror, v -> c().hand.mirror = v));
        r.add(new BreakLineSettingsComponent("Main hand")); transform(r, () -> c().hand.main);
        int off = r.size(); r.add(new BreakLineSettingsComponent("Off hand")); transform(r, () -> c().hand.off);
        visible(r, off, () -> !c().hand.mirror);
        r.add(new BreakLineSettingsComponent("Animation / FOV"));
        r.add(toggle("Equip animation", () -> c().hand.equipAnimation, v -> c().hand.equipAnimation = v));
        r.add(decimal("Swing speed", () -> c().hand.swingSpeed, v -> c().hand.swingSpeed = v, 0.1, 5));
        r.add(toggle("Custom hand FOV", () -> c().hand.fovEnabled, v -> c().hand.fovEnabled = v));
        r.add(number("Hand FOV", () -> c().hand.fov, v -> c().hand.fov = v, 30, 130).withVisibility(() -> c().hand.fovEnabled));
        visible(r, start, () -> c().hand.enabled);
    }
    private static void transform(List<SettingsComponent> r, Supplier<VisualsConfig.Transform> t) {
        r.add(decimal("X offset", () -> t.get().x, v -> t.get().x = v, -3, 3));
        r.add(decimal("Y offset", () -> t.get().y, v -> t.get().y = v, -3, 3));
        r.add(decimal("Z offset", () -> t.get().z, v -> t.get().z = v, -3, 3));
        r.add(number("Pitch", () -> t.get().pitch, v -> t.get().pitch = v, -180, 180));
        r.add(number("Yaw", () -> t.get().yaw, v -> t.get().yaw = v, -180, 180));
        r.add(number("Roll", () -> t.get().roll, v -> t.get().roll = v, -180, 180));
        r.add(decimal("Scale", () -> t.get().scale, v -> t.get().scale = v, 0.1, 3));
    }
    private static void camera(List<SettingsComponent> r) {
        r.add(toggle("Freelook", () -> c().camera.enabled, v -> c().camera.enabled = v));
        r.add(bind("Freelook key", () -> c().camera.key, v -> c().camera.key = v, () -> c().camera.modifiers, v -> c().camera.modifiers = v));
        r.add(cycle("Key mode", () -> c().camera.mode, v -> c().camera.mode = v, VisualsConfig.LookMode.values()));
        r.add(decimal("Freelook distance", () -> c().camera.distance, v -> c().camera.distance = v, 0.5, 20));
        r.add(toggle("Front view", () -> c().camera.frontView, v -> c().camera.frontView = v));
        r.add(toggle("Invert pitch", () -> c().camera.invertPitch, v -> c().camera.invertPitch = v));
        r.add(toggle("Custom third person distance", () -> c().camera.distanceEnabled, v -> c().camera.distanceEnabled = v));
        r.add(decimal("Third person distance", () -> c().camera.thirdPersonDistance, v -> c().camera.thirdPersonDistance = v, 0.5, 20).withVisibility(() -> c().camera.distanceEnabled));
    }
    private static void world(List<SettingsComponent> r) {
        r.add(toggle("Custom brightness", () -> c().brightnessEnabled, v -> c().brightnessEnabled = v));
        r.add(number("Brightness (%)", () -> c().brightness, v -> c().brightness = v, 0, 200).withVisibility(() -> c().brightnessEnabled));
        r.add(toggle("Sky light tint", () -> c().skyLightEnabled, v -> c().skyLightEnabled = v));
        r.add(rgbColor("Sky light color", () -> c().skyLightColor, v -> c().skyLightColor = v).withVisibility(() -> c().skyLightEnabled));
        r.add(toggle("Block light tint", () -> c().blockLightEnabled, v -> c().blockLightEnabled = v));
        r.add(rgbColor("Block light color", () -> c().blockLightColor, v -> c().blockLightColor = v).withVisibility(() -> c().blockLightEnabled));
        r.add(toggle("Custom sky color", () -> c().skyColorEnabled, v -> c().skyColorEnabled = v));
        r.add(rgbColor("Sky color", () -> c().skyColor, v -> c().skyColor = v).withVisibility(() -> c().skyColorEnabled));
        r.add(toggle("Custom fog color", () -> c().fogColorEnabled, v -> c().fogColorEnabled = v));
        r.add(rgbColor("Fog color", () -> c().fogColor, v -> c().fogColor = v).withVisibility(() -> c().fogColorEnabled));
        r.add(toggle("Custom cloud color", () -> c().cloudColorEnabled, v -> c().cloudColorEnabled = v));
        r.add(color("Cloud color / alpha", () -> c().cloudColor, v -> c().cloudColor = v).withVisibility(() -> c().cloudColorEnabled));
        r.add(cycle("Fog", () -> c().fogMode, v -> c().fogMode = v, VisualsConfig.FogMode.values()));
        r.add(decimal("Fog distance multiplier", () -> c().fogDistance, v -> c().fogDistance = v, 0.1, 10).withVisibility(() -> c().fogMode == VisualsConfig.FogMode.CUSTOM));
        r.add(toggle("Fluid / snow fog", () -> c().fluidFog, v -> c().fluidFog = v));
        r.add(toggle("Custom time", () -> c().timeEnabled, v -> c().timeEnabled = v));
        r.add(number("Time (ticks)", () -> c().time, v -> c().time = v, 0, 23999).withVisibility(() -> c().timeEnabled));
        r.add(cycle("Weather", () -> c().weather, v -> c().weather = v, VisualsConfig.Weather.values()));
        r.add(number("Shadow strength (%)", () -> c().shadowStrength, v -> c().shadowStrength = v, 0, 200));
        r.add(number("Shadow radius (%)", () -> c().shadowRadius, v -> c().shadowRadius = v, 0, 200));
    }
    private static void tab(List<SettingsComponent> r) {
        r.add(toggle("Custom TAB", () -> c().tab.enabled, v -> c().tab.enabled = v)); int start = r.size();
        r.add(toggle("Interactive cursor", () -> c().tab.interactive, v -> c().tab.interactive = v));
        r.add(bind("Cursor key (while TAB held)", () -> c().tab.cursorKey, v -> c().tab.cursorKey = v, () -> c().tab.cursorModifiers, v -> c().tab.cursorModifiers = v));
        r.add(toggle("Scroll pages", () -> c().tab.scrollPaging, v -> c().tab.scrollPaging = v));
        r.add(number("Rows per column", () -> c().tab.rows, v -> c().tab.rows = v, 1, 40));
        r.add(number("Columns per page", () -> c().tab.columns, v -> c().tab.columns = v, 1, 8));
        r.add(number("Column width (0: auto)", () -> c().tab.columnWidth, v -> c().tab.columnWidth = v, 0, 600));
        r.add(color("Hovered player color", () -> c().tab.highlight, v -> c().tab.highlight = v));
        r.add(toggle("Page / controls hint", () -> c().tab.pageHint, v -> c().tab.pageHint = v));
        visible(r, start, () -> c().tab.enabled);
    }
    private static void visible(List<SettingsComponent> r, int start, BooleanSupplier condition) {
        for (int i = start; i < r.size(); i++) { var item = r.get(i); var old = item.visibleWhen; item.withVisibility(() -> condition.getAsBoolean() && old.getAsBoolean()); }
    }
    private static String title(Enum<?> value) {
        if (value instanceof AspectRatioPreset preset) return preset.title;
        return value.name().charAt(0) + value.name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
    }
    private static <E extends Enum<E>> SettingsComponent cycle(String label, Supplier<E> get, Consumer<E> set, E[] values) {
        return new CycleSettingsComponent<>(0, 0, 0, 0, label, get, set, values, HudSettingsVisualsSection::title);
    }
    private static SettingsComponent toggle(String label, Supplier<Boolean> get, Consumer<Boolean> set) { return new ToggleSettingsComponent(0, 0, 0, 0, label, get, set); }
    private static SettingsComponent rgbColor(String label, IntSupplier get, IntConsumer set) {
        return new ColorPickerSettingsComponent(label, () -> get.getAsInt() & 0xFFFFFF, value -> set.accept(0xFF000000 | value));
    }
    private static SettingsComponent color(String label, IntSupplier get, IntConsumer set) { return new ColorPickerSettingsComponent(label, get, set).withAlpha(); }
    private static SettingsComponent bind(String label, IntSupplier get, IntConsumer set, IntSupplier modifiers, IntConsumer modifierSetter) { return new KeybindSettingsComponent(0, 0, 0, 0, label, get, set).withModifiers(modifiers, modifierSetter); }
    private static SettingsComponent number(String label, IntSupplier get, IntConsumer set, int min, int max) { return new DragNumberSettingsComponent(0, 0, 0, 0, label, get, set, min, max); }
    private static SettingsComponent decimal(String label, DoubleSupplier get, DoubleConsumer set, double min, double max) {
        return new DragNumberSettingsComponent(0, 0, 0, 0, label, () -> (int) Math.round(get.getAsDouble() * 100), v -> set.accept(v / 100.0),
                (int) Math.round(min * 100), (int) Math.round(max * 100), 5).withFormatter(v -> String.format(Locale.ROOT, "%.2f", v / 100.0));
    }
}
