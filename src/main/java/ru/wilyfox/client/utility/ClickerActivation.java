package ru.wilyfox.client.utility;

import ru.wilyfox.client.hud.config.ClickerConfig.Activation;

/** Toggle state is separate from momentary hold state, including while a menu has focus. */
public final class ClickerActivation {
    private Activation previous;
    private boolean toggled;
    public boolean update(Activation mode, int presses, boolean held, boolean allowed) {
        if (mode != previous) { toggled = false; previous = mode; }
        if (allowed && mode == Activation.TOGGLE && (presses & 1) != 0) toggled = !toggled;
        return allowed && (mode == Activation.HOLD ? held : toggled);
    }
    public void reset() { toggled = false; }
}
