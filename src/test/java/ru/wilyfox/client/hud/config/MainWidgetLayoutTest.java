package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.indicators.ScreenAnchor;
import ru.wilyfox.client.hud.widget.WidgetCorner;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MainWidgetLayoutTest {
    private final Gson gson = new Gson();

    @Test
    void newInstallHasAnEmptyLayoutDespiteLegacyDefaults() {
        var config = HudConfigSanitizer.sanitize(new HudConfig());
        assertTrue(config.mainLayout.widgets.isEmpty());
        assertFalse(WidgetCatalog.isAdded(config, "BossHudWidget"));
    }

    @Test
    void migratesVisibleWidgetsWithoutChangingPlacementsOrAppearance() {
        var config = decode("""
                {"bossWidget":{"active":true,"maxBosses":7},
                 "scoreboard":{"active":false},"fishing":{"showFishingQuestsWidget":true},
                 "widgetLayouts":{"BossHudWidget":{"x":37,"y":92,"scale":1.4,
                    "anchor":"TOP_LEFT","snapTarget":"FishingQuestsWidget",
                    "snapOwnCorner":"TOP_LEFT","snapTargetCorner":"BOTTOM_LEFT"}}}
                """);
        assertTrue(config.mainLayout.widgets.contains("BossHudWidget"));
        assertTrue(config.mainLayout.widgets.contains("FishingQuestsWidget"));
        assertFalse(config.mainLayout.widgets.contains("ScoreboardWidget"));
        var placement = config.widgetLayouts.get("BossHudWidget");
        assertEquals(37, placement.x);
        assertEquals(92, placement.y);
        assertEquals(1.4f, placement.scale);
        assertEquals(ScreenAnchor.TOP_LEFT, placement.anchor);
        assertEquals("FishingQuestsWidget", placement.snapTarget);
        assertEquals(WidgetCorner.TOP_LEFT, placement.snapOwnCorner);
        assertEquals(WidgetCorner.BOTTOM_LEFT, placement.snapTargetCorner);
        assertEquals(7, config.bossWidget.maxBosses);
    }

    @Test
    void previouslyHiddenWidgetStaysOutOfTheLayoutAndKeepsItsSavedPlacement() {
        var config = decode("""
                {"bossWidget":{"active":true},
                 "widgetLayouts":{"BossHudWidget":{"x":41,"scale":1.25,"hiddenInGameplay":true}}}
                """);
        assertFalse(config.mainLayout.widgets.contains("BossHudWidget"));
        assertEquals(41, config.widgetLayouts.get("BossHudWidget").x);
        assertEquals(1.25f, config.widgetLayouts.get("BossHudWidget").scale);
    }

    @Test
    void emptySavedLayoutDoesNotMigrateAgainOrRestoreDeletedWidgets() {
        var config = decode("""
                {"mainLayout":{"widgets":[]},"bossWidget":{"active":true},
                 "widgetLayouts":{"BossHudWidget":{"x":37,"scale":1.4}}}
                """);
        assertTrue(config.mainLayout.widgets.isEmpty());
        assertTrue(decode(gson.toJson(config)).mainLayout.widgets.isEmpty());
        assertEquals(37, config.widgetLayouts.get("BossHudWidget").x);
    }

    @Test
    void membershipIsAuthoritativeEvenWhenOldToggleDisagrees() {
        var config = decode("""
                {"mainLayout":{"widgets":["ScoreboardWidget","FishingQuestsWidget"]},
                 "scoreboard":{"active":false},"fishing":{"showFishingQuestsWidget":false}}
                """);
        assertEquals(Set.of("ScoreboardWidget", "FishingQuestsWidget"), config.mainLayout.widgets);
        assertTrue(WidgetCatalog.isAdded(config, "ScoreboardWidget"));
        assertTrue(WidgetCatalog.isAdded(config, "FishingQuestsWidget"));
    }

    @Test
    void removesUnknownRemovedAndNullWidgetIdsAndRepairsNullContainers() {
        var config = decode("""
                {"mainLayout":{"widgets":["PotionRecipeWidget","UnknownWidget",null,"BossHudWidget","BossHudWidget"]}}
                """);
        assertEquals(Set.of("BossHudWidget"), config.mainLayout.widgets);
        assertTrue(decode("{\"mainLayout\":null}").mainLayout.widgets.isEmpty());
        assertTrue(decode("{\"mainLayout\":{\"widgets\":null}}").mainLayout.widgets.isEmpty());
    }

    private HudConfig decode(String json) { return HudConfigCodec.decode(gson, JsonParser.parseString(json)); }

    @Test
    void savedConfigUsesOnlyLayoutMembershipAndKeepsFeatureToggles() {
        var codec = HudConfigCodec.createGson();
        var config = decode("""
                {"bossWidget":{"active":true},"fishing":{"showFishingQuestsWidget":true},
                 "widgetLayouts":{"BossHudWidget":{"x":41,"hiddenInGameplay":true}}}
                """);
        var saved = JsonParser.parseString(codec.toJson(config)).getAsJsonObject();
        assertFalse(saved.getAsJsonObject("bossWidget").has("active"));
        assertFalse(saved.getAsJsonObject("fishing").has("showFishingQuestsWidget"));
        assertFalse(saved.getAsJsonObject("widgetLayouts").getAsJsonObject("BossHudWidget").has("hiddenInGameplay"));
        assertTrue(saved.getAsJsonObject("quickAccess").has("active"));
        assertTrue(saved.getAsJsonObject("playerHealthBars").has("active"));
        assertTrue(saved.has("mainLayout"));
        assertEquals(config.mainLayout.widgets, HudConfigCodec.decode(codec, saved).mainLayout.widgets);
    }
}
