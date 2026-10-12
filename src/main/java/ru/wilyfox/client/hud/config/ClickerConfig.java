package ru.wilyfox.client.hud.config;

public class ClickerConfig {
    public enum Activation { TOGGLE, HOLD }
    public Activation activation = Activation.TOGGLE;
    public int cps = 20;
    public boolean useItem = false;
    public void sanitize() {
        if (activation == null) activation = Activation.TOGGLE;
        cps = Math.clamp(cps, 1, 20);
    }
}
