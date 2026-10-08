package ru.wilyfox.client.hud.config;

public class VisualsConfig {
    public AspectRatioPreset aspectRatio = AspectRatioPreset.OFF;
    public int aspectWidth = 1440;
    public int aspectHeight = 1080;
    public boolean stretchHand = true;
    public boolean blockOutline = false;
    public int blockOutlineColor = 0xFF43D5FF;
    public int blockOutlineWidth = 2;
    public boolean skyColorEnabled = false;
    public int skyColor = 0xFF78A7FF;
    public boolean fogColorEnabled = false;
    public int fogColor = 0xFFC0D8FF;

    public double ratio() {
        if (aspectRatio == null || aspectRatio == AspectRatioPreset.OFF) return 0;
        if (aspectRatio != AspectRatioPreset.CUSTOM) return aspectRatio.ratio;
        return Math.clamp(Math.max(1, aspectWidth) / (double) Math.max(1, aspectHeight), 0.25, 4.0);
    }

    public void sanitize() {
        if (aspectRatio == null) aspectRatio = AspectRatioPreset.OFF;
        aspectWidth = Math.clamp(aspectWidth, 1, 8192);
        aspectHeight = Math.clamp(aspectHeight, 1, 8192);
        blockOutlineWidth = Math.clamp(blockOutlineWidth, 1, 8);
    }
}
