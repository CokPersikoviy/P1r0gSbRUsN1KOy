package ru.wilyfox.client.hud.config;

public class PlayerHealthBarsConfig {
    public boolean active = true;
    public boolean showNumericHp = true;
    public int verticalOffset = 0;
    public int opacityPercent = 100;
    public boolean distanceFade = true;

    // Bar dimensions as a percentage of the base size.
    public int sizePercent = 100;
    // Health fraction (percent) below which the bar shifts toward the hard accent colour.
    public int hardAccentThresholdPercent = 35;
    // How strongly the colour snaps to the hard accent below the threshold (0 = none, 100 = full).
    public int accentStrengthPercent = 100;
}
