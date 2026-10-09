package ru.wilyfox.client.hud.config;

import java.util.LinkedHashSet;
import java.util.Set;

public final class WidgetLocationConfig {
    public Set<String> locationVisibility = new LinkedHashSet<>();
    public Set<String> locationHidden = new LinkedHashSet<>();
    public void selectVisible(Set<String> locations) {
        locationVisibility = LocationSelectors.sanitize(locations);
        locationHidden.clear();
    }
    public void selectHidden(Set<String> locations) {
        locationHidden = LocationSelectors.sanitize(locations);
        locationVisibility.clear();
    }
    public boolean isVisible(String currentLocation) {
        if (!locationVisibility.isEmpty()) return LocationSelectors.matches(locationVisibility, currentLocation);
        return !LocationSelectors.matches(locationHidden, currentLocation);
    }
    public void sanitize() {
        locationVisibility = LocationSelectors.sanitize(locationVisibility);
        locationHidden = LocationSelectors.sanitize(locationHidden);
        if (!locationVisibility.isEmpty()) locationHidden.clear();
    }
}
