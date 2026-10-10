package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisualsConfigTest {
    @Test void legacyRgbMigrationRunsOnceAndModernAlphaAndFractionalWidthsSurviveReload() {
        var gson = new Gson();
        var c = gson.fromJson("{\"blockOutlineColor\":1122867,\"skyColor\":4478310,\"fogColor\":7833753,\"blockOutlineWidth\":2}", VisualsConfig.class);
        c.sanitize();
        assertEquals(0xFF112233, c.blockOutlineColor);
        assertEquals(0xFF445566, c.skyColor); assertEquals(0xFF778899, c.fogColor);
        c.blockOutlineColor = 0x00112233; c.blockOutlineWidth = 2.55;
        var saved = gson.fromJson(gson.toJson(c), VisualsConfig.class); saved.sanitize();
        assertEquals(0x00112233, saved.blockOutlineColor); assertEquals(2.55, saved.blockOutlineWidth);
    }
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
    @Test void newVisualGroupsStayOffInExistingConfigsAndRecoverNulls() {
        var c = new Gson().fromJson("{\"aspectRatio\":\"R4_3\",\"blockOutline\":true,\"blockOutlineColor\":-15654349,\"hand\":null,\"crosshair\":null,\"tab\":null}", VisualsConfig.class);
        c.sanitize();
        assertTrue(c.blockOutline); assertEquals(0xFF112233, c.blockOutlineColor);
        assertFalse(c.advancedOutline()); assertFalse(c.hand.enabled); assertFalse(c.crosshair.enabled);
        assertFalse(c.hitboxes.show); assertFalse(c.camera.enabled); assertFalse(c.tab.enabled); assertFalse(c.compactReload);
        assertEquals(4.0 / 3, c.ratio(), 1e-6);
    }
    @Test void malformedGeometryAndBindingsCannotCreateUnboundedWork() {
        var c = new VisualsConfig(); c.crosshair.thickness = Double.NaN; c.crosshair.gap = Double.POSITIVE_INFINITY;
        c.hand.main.scale = Double.NEGATIVE_INFINITY; c.camera.key = 9999; c.hitboxes.width = 9999;
        c.tab.rows = 0; c.tab.columns = Integer.MAX_VALUE; c.fogDistance = -10; c.blockRainbowSpeed = 0;
        c.weather = null; c.fogMode = null; c.blockStyle = null; c.hand.off = null;
        c.sanitize();
        assertEquals(2, c.crosshair.thickness); assertEquals(3, c.crosshair.gap); assertEquals(1, c.hand.main.scale);
        assertEquals(-1, c.camera.key); assertEquals(8, c.hitboxes.width); assertEquals(1, c.tab.rows); assertEquals(8, c.tab.columns);
        assertEquals(0.1, c.fogDistance); assertEquals(1, c.blockRainbowSpeed); assertNotNull(c.hand.off);
        assertEquals(VisualsConfig.Weather.SERVER, c.weather); assertEquals(VisualsConfig.FogMode.DEFAULT, c.fogMode);
    }
    @Test void everyNewGroupRoundTripsWithoutLosingAlphaOrOffHandValues() {
        var c = new VisualsConfig(); c.crosshair.enabled = true; c.crosshair.style = VisualsConfig.CrosshairStyle.X;
        c.crosshair.outlineColor = 0x23112233; c.hitboxes.targets = VisualsConfig.Targets.PLAYERS;
        c.hand.mirror = false; c.hand.off.x = -0.35; c.camera.key = 1004; c.tab.enabled = true;
        c.blockStyle = VisualsConfig.LineStyle.CORNERS; c.blockFillColor = 0x19223344; c.weather = VisualsConfig.Weather.THUNDER;
        var gson = new Gson(); var saved = gson.fromJson(gson.toJson(c), VisualsConfig.class); saved.sanitize();
        assertTrue(saved.crosshair.enabled); assertEquals(c.crosshair.style, saved.crosshair.style);
        assertEquals(c.crosshair.outlineColor, saved.crosshair.outlineColor); assertFalse(saved.hand.mirror);
        assertEquals(c.hand.off.x, saved.hand.off.x); assertEquals(1004, saved.camera.key); assertTrue(saved.tab.enabled);
        assertEquals(c.blockFillColor, saved.blockFillColor); assertEquals(c.weather, saved.weather);
    }
}
