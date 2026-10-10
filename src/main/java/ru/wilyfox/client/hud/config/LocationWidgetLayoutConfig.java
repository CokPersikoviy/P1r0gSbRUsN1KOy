package ru.wilyfox.client.hud.config;

import java.util.*;

/** Location layout optionally includes main widgets; its own placements take precedence. */
public final class LocationWidgetLayoutConfig {
    public String name = "Layout";
    public boolean inheritMainLayout = false;
    public Set<String> locationVisibility = new LinkedHashSet<>();
    public Set<String> widgets = new LinkedHashSet<>();
    public Map<String, WidgetLayoutConfig> placements = new LinkedHashMap<>();
    public boolean matches(String location) { return LocationSelectors.matches(locationVisibility, location); }
}
