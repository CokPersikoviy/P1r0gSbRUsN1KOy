package ru.wilyfox.client.hud.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ColorPickerSettingsComponentTest {
    @Test
    void formatsAndParsesSixDigitHexColors() {
        assertEquals("#00A1FF", ColorPickerSettingsComponent.formatHex(0x00A1FF));
        assertEquals(0x00A1FF, ColorPickerSettingsComponent.parseHex("#00a1ff"));
        assertEquals(0xABCDEF, ColorPickerSettingsComponent.parseHex("ABCDEF"));
        assertNull(ColorPickerSettingsComponent.parseHex("#123"));
        assertNull(ColorPickerSettingsComponent.parseHex("#GG0000"));
    }

    @Test
    void hsvConversionPreservesPrimaryAndThemeColors() {
        assertRoundTrip(0xFF0000);
        assertRoundTrip(0x00FF00);
        assertRoundTrip(0x0000FF);
        assertRoundTrip(0xD0D0D0);
        assertRoundTrip(0xEB4242);
    }

    private static void assertRoundTrip(int rgb) {
        ColorPickerSettingsComponent.Hsv hsv = ColorPickerSettingsComponent.rgbToHsv(rgb);
        assertEquals(rgb, ColorPickerSettingsComponent.hsvToRgb(
                hsv.hue(),
                hsv.saturation(),
                hsv.value()
        ));
    }
}
