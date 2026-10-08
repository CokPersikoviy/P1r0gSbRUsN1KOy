package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudConfigSanitizerTest {
    private final Gson gson = new Gson();

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
