package ru.wilyfox.client.hud.config;

import java.util.*;

/** Additional layout; main membership and placements remain in their original config fields. */
public final class LocationWidgetLayoutConfig {
    public String name = "Layout";
    public Set<String> locationVisibility = new LinkedHashSet<>();
    public Set<String> widgets = new LinkedHashSet<>();
    public Map<String, WidgetLayoutConfig> placements = new LinkedHashMap<>();
    public boolean matches(String location) { return LocationSelectors.matches(locationVisibility, location); }
}
