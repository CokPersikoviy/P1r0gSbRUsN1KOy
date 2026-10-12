package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudConfigSanitizerTest {
    private final Gson gson = new Gson();

    @Test void legacySnapsHaveZeroOffsetsAndNewPreferencesAreSanitized() {
        var old = HudConfigSanitizer.sanitize(gson.fromJson("""
            {"widgetLayouts":{"BossHudWidget":{"snapTarget":"BoostersWidget","snapOwnCorner":"TOP_LEFT","snapTargetCorner":"TOP_RIGHT"}}}
            """, HudConfig.class));
        assertTrue(old.snapping.enabled); assertEquals(HudSnapConfig.Behavior.CONNECT, old.snapping.behavior);
        assertEquals(0, old.widgetLayouts.get("BossHudWidget").snapOffsetX);
        assertEquals(0, old.widgetLayouts.get("BossHudWidget").anchorOffsetY);
        old.snapping.capturePixels = 24; old.snapping.releasePixels = 1; old.snapping.gap = -3;
        old.widgetLayouts.get("BossHudWidget").snapOffsetX = Integer.MAX_VALUE;
        var local = new LocationWidgetLayoutConfig(); local.widgets.add("BossHudWidget");
        var placement = new WidgetLayoutConfig(); placement.anchorOffsetY = Integer.MIN_VALUE;
        local.placements.put("BossHudWidget", placement); old.locationLayouts.put("test", local);
        HudConfigSanitizer.sanitize(old);
        assertEquals(24, old.snapping.releasePixels); assertEquals(0, old.snapping.gap);
        assertEquals(10000, old.widgetLayouts.get("BossHudWidget").snapOffsetX);
        assertEquals(-10000, local.placements.get("BossHudWidget").anchorOffsetY);
    }

    @Test void oldConfigsKeepToggleClickerAndNewMiningSettingsHaveSafeDefaults() {
        var old = HudConfigSanitizer.sanitize(gson.fromJson("{\"clicker\":{\"cps\":10}}", HudConfig.class));
        assertEquals(ClickerConfig.Activation.TOGGLE, old.clicker.activation);
        assertEquals(MiningStatisticsConfig.Layout.SUMMARY, old.miningStatistics.layout);
        assertFalse(old.miningStatistics.showTitle);
        assertFalse(old.miningStatistics.showStatus);
        assertEquals(10, old.miningStatistics.idleSeconds);
        var custom = HudConfigSanitizer.sanitize(gson.fromJson("""
                {"clicker":{"activation":"HOLD","cps":999},
                 "miningStatistics":{"sources":[],"idleSeconds":0,"decimals":7,"minimumWidth":-5}}
                """, HudConfig.class));
        assertEquals(ClickerConfig.Activation.HOLD, custom.clicker.activation);
        assertEquals(20, custom.clicker.cps);
        assertTrue(custom.miningStatistics.sources.isEmpty());
        assertEquals(1, custom.miningStatistics.idleSeconds);
        assertEquals(2, custom.miningStatistics.decimals);
        assertEquals(60, custom.miningStatistics.minimumWidth);
    }

    @Test
    void migratesOldLowHpSettingsAndRestoresMissingFormatParts() {
        HudConfig config = gson.fromJson("""
                {"bossRespawnMessages":{"lowHealthMessage":true,"lowHealthPercent":20,
                  "lowHealthFormat":{"elements":{"NAME":{"visible":false,"colorCode":"&a"},
                    "HEALTH":{"colorCode":"&c"},"LEVEL":null,"STAGE":{"colorCode":null}}}}}
                """, HudConfig.class);
        config = HudConfigSanitizer.sanitize(config);
        var format = config.bossRespawnMessages.lowHealthFormat;
        assertTrue(config.bossRespawnMessages.lowHealthMessage);
        assertEquals(20, config.bossRespawnMessages.lowHealthPercent);
        assertFalse(format.element(LowHpMessageElement.NAME).visible);
        assertEquals("&a", format.element(LowHpMessageElement.NAME).colorCode);
        assertTrue(format.element(LowHpMessageElement.HEALTH).visible);
        assertEquals("&b", format.element(LowHpMessageElement.LEVEL).colorCode);
        assertEquals("&7", format.element(LowHpMessageElement.STAGE).colorCode);

        config.bossRespawnMessages.lowHealthFormat = null;
        config = HudConfigSanitizer.sanitize(config);
        assertNotNull(config.bossRespawnMessages.lowHealthFormat);
        config.bossRespawnMessages.lowHealthFormat.elements = null;
        var missingElements = config;
        assertDoesNotThrow(() -> HudConfigSanitizer.sanitize(missingElements));
        assertEquals(LowHpMessageElement.values().length, config.bossRespawnMessages.lowHealthFormat.elements.size());
    }

    @Test
    void savesVisibilityColorsAndIncompleteInputWithoutDiscardingKeystrokes() {
        var config = new HudConfig();
        var format = config.bossRespawnMessages.lowHealthFormat;
        format.element(LowHpMessageElement.SERVER).visible = false;
        format.element(LowHpMessageElement.NAME).colorCode = "§A";
        format.element(LowHpMessageElement.STAGE).colorCode = "&";
        var restored = HudConfigSanitizer.sanitize(gson.fromJson(gson.toJson(config), HudConfig.class));
        assertFalse(restored.bossRespawnMessages.lowHealthFormat.element(LowHpMessageElement.SERVER).visible);
        assertEquals("§A", restored.bossRespawnMessages.lowHealthFormat.element(LowHpMessageElement.NAME).colorCode);
        assertEquals("&", restored.bossRespawnMessages.lowHealthFormat.element(LowHpMessageElement.STAGE).colorCode);
    }

    @Test
    void removesDeletedPotionWidgetSettingsAndUnhooksOnlyItsDependents() {
        HudConfig config = gson.fromJson("""
                {"potionRecipe":{"active":true,"visibility":"ALCHEMY"},
                 "alchemy":{"recipeActionAlerts":true},"widgetLayouts":{
                   "PotionRecipeWidget":{"x":20,"y":20},
                   "CraftRecipeWidget":{"x":40,"y":60,"snapTarget":"PotionRecipeWidget",
                     "snapOwnCorner":"TOP_LEFT","snapTargetCorner":"BOTTOM_LEFT"},
                   "PotionTimersWidget":{"x":12,"snapTarget":"BossHudWidget"}}}
                """, HudConfig.class);
        config = HudConfigSanitizer.sanitize(config);

        assertFalse(config.widgetLayouts.containsKey("PotionRecipeWidget"));
        var craft = config.widgetLayouts.get("CraftRecipeWidget");
        assertEquals(40, craft.x);
        assertEquals(60, craft.y);
        assertNull(craft.snapTarget);
        assertNull(craft.snapOwnCorner);
        assertNull(craft.snapTargetCorner);
        assertEquals("BossHudWidget", config.widgetLayouts.get("PotionTimersWidget").snapTarget);
        assertTrue(config.alchemy.recipeActionAlerts);
        assertFalse(gson.toJson(config).contains("potionRecipe"));
    }

    @Test
    void repairsNullQuickAccessSectionsAndItemsWithoutAddingExecutableCommands() {
        HudConfig config = gson.fromJson("""
                {"quickAccess":{"sections":[null,{"title":null,"items":null},
                 {"items":[null,{"title":null,"command":null,"itemId":null,"customModelData":-7},
                           {"title":"Keep","command":"warp fish","itemId":"minecraft:fishing_rod"}]}]}}
                """, HudConfig.class);
        config = HudConfigSanitizer.sanitize(config);

        assertEquals(3, config.quickAccess.sections.size());
        assertNotNull(config.quickAccess.sections.getFirst());
        assertEquals("Section", config.quickAccess.sections.get(1).title);
        assertTrue(config.quickAccess.sections.get(1).items.isEmpty());
        var items = config.quickAccess.sections.get(2).items;
        assertEquals(2, items.size());
        assertEquals("", items.getFirst().command);
        assertEquals("minecraft:paper", items.getFirst().itemId);
        assertEquals(0, items.getFirst().customModelData);
        assertEquals("warp fish", items.get(1).command);
    }

    @Test
    void repairsNonFiniteLayoutValuesAndKeepsLegacyMigration() {
        HudConfig config = gson.fromJson("""
                {"lastWindowWidth":1000,"lastWindowHeight":500,"widgetLayouts":{
                 "Invalid":{"x":250,"y":100,"xFraction":"NaN","yFraction":12,"scale":"Infinity"},
                 "Legacy":{"x":200,"y":150},"Empty":null}}
                """, HudConfig.class);
        config = HudConfigSanitizer.sanitize(config);

        var invalid = config.widgetLayouts.get("Invalid");
        assertEquals(0.25, invalid.xFraction);
        assertEquals(1.0, invalid.yFraction);
        assertEquals(1f, invalid.scale);
        var legacy = config.widgetLayouts.get("Legacy");
        assertEquals(0.2, legacy.xFraction);
        assertEquals(0.3, legacy.yFraction);
        assertDoesNotThrow(() -> gson.toJson(invalid));
    }

    @Test
    void missingGroupsReceiveDefaultsWithoutDiscardingSavedTheme() {
        HudConfig config = gson.fromJson("""
                {"render":null,"theme":{"preset":"CUSTOM","customAccentRed":12,
                 "customAccentGreen":34,"customAccentBlue":56,"widgetBackgroundOpacityPercent":99}}
                """, HudConfig.class);
        config = HudConfigSanitizer.sanitize(config);

        assertEquals(WidgetChrome.FROST, config.render.widgetChrome);
        assertEquals(ThemePreset.CUSTOM, config.theme.preset);
        assertEquals(12, config.theme.customAccentRed);
        assertEquals(50, config.theme.widgetBackgroundOpacityPercent);
    }
}
