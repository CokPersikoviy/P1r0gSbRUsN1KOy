package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisualsConfigTest {
    @Test void oldConfigsKeepVanillaVisuals() {
        HudConfig config = HudConfigSanitizer.sanitize(new Gson().fromJson("{\"visuals\":null,\"render\":{\"staticHand\":true}}", HudConfig.class));
        assertEquals(0, config.visuals.ratio());
        assertFalse(config.visuals.blockOutline);
        assertFalse(config.visuals.skyColorEnabled);
        assertFalse(config.visuals.fogColorEnabled);
        assertTrue(config.render.staticHand);
    }
    @Test void presetsAndCustomValuesUseSafeRatios() {
        var config = new VisualsConfig();
        config.aspectRatio = AspectRatioPreset.R4_3;
        assertEquals(4.0 / 3, config.ratio(), 1e-6);
        config.aspectRatio = AspectRatioPreset.CUSTOM;
        config.aspectWidth = 2560; config.aspectHeight = 1440;
        assertEquals(16.0 / 9, config.ratio(), 1e-6);
        config.aspectHeight = 0;
        assertEquals(4, config.ratio());
        config.aspectWidth = 0; config.aspectHeight = 8192;
        assertEquals(0.25, config.ratio());
        config.aspectRatio = null;
        assertEquals(0, config.ratio());
    }
    @Test void invalidPersistedValuesAreNormalized() {
        var config = new VisualsConfig();
        config.aspectRatio = null;
        config.aspectWidth = -1; config.aspectHeight = Integer.MAX_VALUE; config.blockOutlineWidth = 99;
        config.sanitize();
        assertEquals(AspectRatioPreset.OFF, config.aspectRatio);
        assertEquals(1, config.aspectWidth);
        assertEquals(8192, config.aspectHeight);
        assertEquals(8, config.blockOutlineWidth);
        config.blockOutlineWidth = -1; config.sanitize();
        assertEquals(1, config.blockOutlineWidth);
    }
    @Test void settingsRoundTripPreservesIndependentSwitchesAndColors() {
        var gson = new Gson(); var config = new VisualsConfig();
        config.aspectRatio = AspectRatioPreset.R21_9; config.stretchHand = false;
        config.blockOutline = true; config.blockOutlineColor = 0xFF112233;
        config.skyColorEnabled = true; config.skyColor = 0xFF445566;
        config.fogColorEnabled = false; config.fogColor = 0xFF778899;
        var saved = gson.fromJson(gson.toJson(config), VisualsConfig.class);
        assertEquals(config.aspectRatio, saved.aspectRatio);
        assertFalse(saved.stretchHand); assertTrue(saved.blockOutline);
        assertTrue(saved.skyColorEnabled); assertFalse(saved.fogColorEnabled);
        assertEquals(config.blockOutlineColor, saved.blockOutlineColor);
        assertEquals(config.skyColor, saved.skyColor); assertEquals(config.fogColor, saved.fogColor);
    }
}
