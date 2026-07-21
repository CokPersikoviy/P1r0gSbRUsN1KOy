package ru.wilyfox.client.hud.config;

public class ThemeConfig {
    public ThemePreset preset = ThemePreset.FROG;
    public int customAccentRed = 208;
    public int customAccentGreen = 208;
    public int customAccentBlue = 208;
    public int widgetBackgroundOpacityPercent = 34;

    // Strong "critical" accent applied to the negative boss timer and to a player's critically-low
    // health bar. Preset-independent so it stays vivid on any theme. Default: a readable strong red.
    public int hardAccentRed = 235;
    public int hardAccentGreen = 66;
    public int hardAccentBlue = 66;
}
