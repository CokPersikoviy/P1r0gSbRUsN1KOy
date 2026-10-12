package ru.wilyfox.client.hud.config;

public final class HudSnapConfig {
    public enum Behavior {
        ALIGN_ONLY("Align only"), CONNECT("Connect / anchor");
        private final String title;
        Behavior(String title) { this.title = title; }
        public String title() { return title; }
    }
    public boolean enabled = true, showGuides = true, screenTargets = true, edgeAlignment = true, centerAlignment = true, equalSpacing = true;
    public Behavior behavior = Behavior.CONNECT;
    public int capturePixels = 8, releasePixels = 12, gap = 5;
    public void sanitize() {
        if (behavior == null) behavior = Behavior.CONNECT;
        capturePixels = Math.clamp(capturePixels, 2, 24);
        releasePixels = Math.clamp(releasePixels, capturePixels, 48);
        gap = Math.clamp(gap, 0, 30);
    }
}
