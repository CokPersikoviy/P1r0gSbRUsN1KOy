package ru.wilyfox.client.hud.config;

public final class ImageWidgetConfig {
    public String asset = "";
    public int width = 160, height = 120;
    public boolean keepAspectRatio = true, animateGif = true;
    public void sanitize() {
        if (asset == null || !asset.matches("[a-f0-9]{64}\\.img")) asset = "";
        width = Math.clamp(width, 40, 600); height = Math.clamp(height, 40, 600);
    }
}
