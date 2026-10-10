package ru.wilyfox.client.hud.config;

import java.util.*;

/** Select one location layout, with the main layout as fallback. Widget identities remain shared. */
public final class HudLayoutResolver {
    public static final String MAIN = "";
    private HudLayoutResolver() {}
    public static Set<String> widgets(HudConfig config, String editorLayout, String location) {
        if (editorLayout != null) {
            var selected = config.locationLayouts.get(editorLayout);
            return selected == null ? config.mainLayout.widgets : selected.widgets;
        }
        var selected = runtimeLayout(config, location);
        var widgets = new LinkedHashSet<String>();
        if (selected == null || selected.inheritMainLayout) widgets.addAll(config.mainLayout.widgets);
        if (selected != null) widgets.addAll(selected.widgets);
        return widgets;
    }
    public static WidgetLayoutConfig placement(HudConfig config, String key, String editorLayout, String location) {
        if (editorLayout != null) {
            var selected = config.locationLayouts.get(editorLayout);
            return selected == null ? config.widgetLayouts.get(key) : selected.placements.get(key);
        }
        var selected = runtimeLayout(config, location);
        if (selected != null && selected.widgets.contains(key)) {
            var placement = selected.placements.get(key);
            if (placement != null) return placement;
        }
        return config.widgetLayouts.get(key);
    }

    public static Map<String, WidgetLayoutConfig> placementStorage(HudConfig config, String key, String editorLayout, String location) {
        if (editorLayout != null) {
            var selected = config.locationLayouts.get(editorLayout);
            return selected == null ? config.widgetLayouts : selected.placements;
        }
        var selected = runtimeLayout(config, location);
        return selected != null && selected.widgets.contains(key) ? selected.placements : config.widgetLayouts;
    }
    private static LocationWidgetLayoutConfig runtimeLayout(HudConfig config, String location) {
        LocationWidgetLayoutConfig selected = null;
        for (var layout : config.locationLayouts.values()) if (layout.matches(location)) selected = layout;
        return selected;
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
