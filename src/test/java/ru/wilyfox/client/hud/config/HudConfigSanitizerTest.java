package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudConfigSanitizerTest {
    private final Gson gson = new Gson();

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
