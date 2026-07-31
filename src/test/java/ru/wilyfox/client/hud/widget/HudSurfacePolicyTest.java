package ru.wilyfox.client.hud.widget;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.config.WidgetChrome;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudSurfacePolicyTest {
    @Test
    void bareAndNativeNeverUseSmoothGeometry() {
        assertFalse(HudSurface.shouldUseSmoothGeometry(WidgetChrome.BARE, false));
        assertFalse(HudSurface.shouldUseSmoothGeometry(WidgetChrome.BARE, true));
        assertFalse(HudSurface.shouldUseSmoothGeometry(WidgetChrome.FROST, true));
        assertTrue(HudSurface.shouldUseSmoothGeometry(WidgetChrome.SOLID, false));
        assertTrue(HudSurface.shouldUseSmoothGeometry(WidgetChrome.FROST, false));
    }

    @Test
    void blurIsExclusiveToCustomFrostRenderer() {
        assertFalse(HudSurface.shouldUseBlur(WidgetChrome.BARE, false));
        assertFalse(HudSurface.shouldUseBlur(WidgetChrome.SOLID, false));
        assertFalse(HudSurface.shouldUseBlur(WidgetChrome.FROST, true));
        assertTrue(HudSurface.shouldUseBlur(WidgetChrome.FROST, false));
    }
}
