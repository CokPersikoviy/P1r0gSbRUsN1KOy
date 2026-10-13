package ru.wilyfox.client.hud.config;

public final class NearbyMineWidgetConfig {
    public int range = 32;
    public boolean showDistance = true, showCoordinates, showZero = true;
    public void sanitize() { range = Math.clamp(range, 1, 32); }
}
