package ru.wilyfox.client.hud.config;

public class ClickerConfig {
    public enum Activation { TOGGLE, HOLD }
    public enum ActionMode { CLICK, HOLD }
    public Activation activation = Activation.TOGGLE;
    public ActionMode actionMode = ActionMode.CLICK;
    public int cps = 20;
    public boolean useItem = false;
    public void sanitize() {
        if (activation == null) activation = Activation.TOGGLE;
        if (actionMode == null) actionMode = ActionMode.CLICK;
        cps = Math.clamp(cps, 1, 20);
    }
}
