package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeConfigTest {
    @Test
    void legacyCustomAccentBecomesPrimaryAndSecondaryUsesItsDefault() {
        ThemeConfig config = new Gson().fromJson(
                """
                        {
                          "customAccentRed": 12,
                          "customAccentGreen": 34,
                          "customAccentBlue": 56
                        }
                        """,
                ThemeConfig.class
        );

        assertEquals(12, config.customAccentRed);
        assertEquals(34, config.customAccentGreen);
        assertEquals(56, config.customAccentBlue);
        assertEquals(159, config.customSecondaryRed);
        assertEquals(215, config.customSecondaryGreen);
        assertEquals(196, config.customSecondaryBlue);
    }

    @Test
    void legacyPresetNamesMapToTheirReplacementThemes() {
        Gson gson = new Gson();

        assertEquals(
                ThemePreset.LINGONBERRY_PIE,
                gson.fromJson("\"FROG\"", ThemePreset.class)
        );
        assertEquals(
                ThemePreset.WILD_FOX,
                gson.fromJson("\"MINT\"", ThemePreset.class)
        );
        assertEquals(
                ThemePreset.SWAMP_FROG,
                gson.fromJson("\"EMBER\"", ThemePreset.class)
        );
        assertEquals(
                ThemePreset.PICK_ME,
                gson.fromJson("\"OCEAN\"", ThemePreset.class)
        );
    }

    @Test
    void replacementPresetsExposeTheRequestedColorPairs() {
        assertPalette(ThemePreset.LINGONBERRY_PIE, 0x92140C, 0x1C0B19);
        assertPalette(ThemePreset.WILD_FOX, 0xF18805, 0x0C0C08);
        assertPalette(ThemePreset.SWAMP_FROG, 0x06D6A0, 0x2E2F2F);
        assertPalette(ThemePreset.PICK_ME, 0xFF2C55, 0xFEF5EF);
    }

    private static void assertPalette(ThemePreset preset, int primary, int secondary) {
        assertEquals(primary, preset.getPrimaryRgb());
        assertEquals(secondary, preset.getSecondaryRgb());
    }
}
