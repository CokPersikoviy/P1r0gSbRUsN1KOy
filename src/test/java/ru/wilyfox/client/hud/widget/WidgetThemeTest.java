package ru.wilyfox.client.hud.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WidgetThemeTest {
    @Test
    void customPaletteKeepsPrimaryAndSecondaryExtrapolationIndependent() {
        WidgetTheme.applyCustomPalette(0xC43B65, 0x2F8DB8, 100);
        ThemeSnapshot baseline = ThemeSnapshot.capture();

        WidgetTheme.applyCustomPalette(0x5A3FC7, 0x2F8DB8, 100);
        ThemeSnapshot primaryChanged = ThemeSnapshot.capture();

        assertNotEquals(baseline.accentLine, primaryChanged.accentLine);
        assertNotEquals(baseline.title, primaryChanged.title);
        assertNotEquals(baseline.primaryText, primaryChanged.primaryText);
        assertNotEquals(baseline.barFill, primaryChanged.barFill);
        assertEquals(baseline.panelBg, primaryChanged.panelBg);
        assertEquals(baseline.panelBgSoft, primaryChanged.panelBgSoft);
        assertEquals(baseline.secondaryText, primaryChanged.secondaryText);
        assertEquals(baseline.mutedText, primaryChanged.mutedText);
        assertEquals(baseline.barBg, primaryChanged.barBg);
        assertEquals(baseline.outlineSoft, primaryChanged.outlineSoft);
        assertEquals(baseline.gridLine, primaryChanged.gridLine);

        WidgetTheme.applyCustomPalette(0xC43B65, 0xB67B2C, 100);
        ThemeSnapshot secondaryChanged = ThemeSnapshot.capture();

        assertEquals(baseline.accentLine, secondaryChanged.accentLine);
        assertEquals(baseline.title, secondaryChanged.title);
        assertEquals(baseline.primaryText, secondaryChanged.primaryText);
        assertEquals(baseline.barFill, secondaryChanged.barFill);
        assertNotEquals(baseline.panelBg, secondaryChanged.panelBg);
        assertNotEquals(baseline.panelBgSoft, secondaryChanged.panelBgSoft);
        assertNotEquals(baseline.secondaryText, secondaryChanged.secondaryText);
        assertNotEquals(baseline.mutedText, secondaryChanged.mutedText);
        assertNotEquals(baseline.barBg, secondaryChanged.barBg);
        assertNotEquals(baseline.outlineSoft, secondaryChanged.outlineSoft);
        assertNotEquals(baseline.gridLine, secondaryChanged.gridLine);
    }

    private record ThemeSnapshot(
            int panelBg,
            int panelBgSoft,
            int accentLine,
            int title,
            int primaryText,
            int secondaryText,
            int mutedText,
            int barBg,
            int barFill,
            int outlineSoft,
            int gridLine
    ) {
        private static ThemeSnapshot capture() {
            return new ThemeSnapshot(
                    WidgetTheme.PANEL_BG,
                    WidgetTheme.PANEL_BG_SOFT,
                    WidgetTheme.ACCENT_LINE,
                    WidgetTheme.TITLE,
                    WidgetTheme.TEXT_PRIMARY,
                    WidgetTheme.TEXT_SECONDARY,
                    WidgetTheme.TEXT_MUTED,
                    WidgetTheme.BAR_BG,
                    WidgetTheme.BAR_FILL,
                    WidgetTheme.OUTLINE_SOFT,
                    WidgetTheme.GRID_LINE
            );
        }
    }
}
