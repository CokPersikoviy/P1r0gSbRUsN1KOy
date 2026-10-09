package ru.wilyfox.client.hud.config;

import java.util.*;

/** Pure layout selection. A widget keeps its identity; later matching layouts override placement. */
public final class HudLayoutResolver {
    public static final String MAIN = "";
    private HudLayoutResolver() {}
    public static Set<String> widgets(HudConfig config, String editorLayout, String location) {
        if (editorLayout != null) {
            var selected = config.locationLayouts.get(editorLayout);
            return selected == null ? config.mainLayout.widgets : selected.widgets;
        }
        var result = new LinkedHashSet<>(config.mainLayout.widgets);
        for (var layout : config.locationLayouts.values()) if (layout.matches(location)) result.addAll(layout.widgets);
        return result;
    }
    public static WidgetLayoutConfig placement(HudConfig config, String key, String editorLayout, String location) {
        if (editorLayout != null) {
            var selected = config.locationLayouts.get(editorLayout);
            return selected == null ? config.widgetLayouts.get(key) : selected.placements.get(key);
        }
        var result = config.widgetLayouts.get(key);
        for (var layout : config.locationLayouts.values()) {
            if (layout.widgets.contains(key) && layout.matches(location)) {
                var replacement = layout.placements.get(key);
                if (replacement != null) result = replacement;
            }
        }
        return result;
    }

    public static Map<String, WidgetLayoutConfig> placementStorage(HudConfig config, String key, String editorLayout, String location) {
        if (editorLayout != null) {
            var selected = config.locationLayouts.get(editorLayout);
            return selected == null ? config.widgetLayouts : selected.placements;
        }
        var result = config.widgetLayouts;
        for (var layout : config.locationLayouts.values()) if (layout.widgets.contains(key) && layout.matches(location)) result = layout.placements;
        return result;
    }
    public static void sanitize(HudConfig config) {
        if (config.locationLayouts == null) config.locationLayouts = new LinkedHashMap<>();
        config.locationLayouts.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null);
        for (var layout : config.locationLayouts.values()) {
            if (layout.name == null || layout.name.isBlank()) layout.name = "Layout";
            layout.name = layout.name.strip();
            layout.locationVisibility = LocationSelectors.sanitize(layout.locationVisibility);
            if (layout.widgets == null) layout.widgets = new LinkedHashSet<>();
            layout.widgets.removeIf(key -> WidgetCatalog.find(key) == null);
            if (layout.placements == null) layout.placements = new LinkedHashMap<>();
            layout.placements.entrySet().removeIf(entry -> WidgetCatalog.find(entry.getKey()) == null || entry.getValue() == null);
            for (var placement : layout.placements.values()) {
                if (placement.scale != null) placement.scale = Float.isFinite(placement.scale) ? Math.clamp(placement.scale, .5f, 3f) : 1f;
                if (placement.xFraction != null) placement.xFraction = Double.isFinite(placement.xFraction) ? Math.clamp(placement.xFraction, 0, 1) : null;
                if (placement.yFraction != null) placement.yFraction = Double.isFinite(placement.yFraction) ? Math.clamp(placement.yFraction, 0, 1) : null;
                if (placement.snapTarget != null && !layout.widgets.contains(placement.snapTarget)) {
                    placement.snapTarget = null; placement.snapOwnCorner = null; placement.snapTargetCorner = null;
                }
                placement.hiddenInGameplay = null;
            }
        }
        if (config.widgetLocations == null) config.widgetLocations = new LinkedHashMap<>();
        config.widgetLocations.entrySet().removeIf(entry -> WidgetCatalog.find(entry.getKey()) == null || entry.getValue() == null);
        config.widgetLocations.values().forEach(WidgetLocationConfig::sanitize);
    }
}
