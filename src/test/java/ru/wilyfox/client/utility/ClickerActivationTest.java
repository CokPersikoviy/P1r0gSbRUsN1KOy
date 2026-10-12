package ru.wilyfox.client.utility;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ru.wilyfox.client.hud.config.ClickerConfig.Activation.*;

class ClickerActivationTest {
    @Test void holdStopsOnReleaseAndIgnoresTheTogglePressQueue() {
        var state = new ClickerActivation();
        assertTrue(state.update(HOLD, 1, true, true));
        assertTrue(state.update(HOLD, 0, true, true));
        assertFalse(state.update(HOLD, 1, false, true));
        assertFalse(state.update(HOLD, 0, true, false));
    }
    @Test void toggleRetainsTheOriginalPressBehaviorButDoesNotClickInMenus() {
        var state = new ClickerActivation();
        assertTrue(state.update(TOGGLE, 1, false, true));
        assertFalse(state.update(TOGGLE, 0, false, false));
        assertTrue(state.update(TOGGLE, 0, false, true));
        assertFalse(state.update(TOGGLE, 1, false, true));
        assertFalse(state.update(TOGGLE, 1, false, false));
        assertFalse(state.update(TOGGLE, 0, false, true));
    }
    @Test void modeChangesAndDisconnectsNeverLeaveAnOldToggleRunning() {
        var state = new ClickerActivation();
        assertTrue(state.update(TOGGLE, 1, false, true));
        assertFalse(state.update(HOLD, 0, false, true));
        assertFalse(state.update(TOGGLE, 0, false, true));
        assertTrue(state.update(TOGGLE, 1, false, true)); state.reset();
        assertFalse(state.update(TOGGLE, 0, false, true));
    }
}
