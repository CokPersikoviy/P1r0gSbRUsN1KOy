package ru.wilyfox.client.hud.widget;

import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.ThemeConfig;
import ru.wilyfox.client.hud.config.ThemePreset;

public final class WidgetTheme {
    public static int PANEL_BG = 0x90131313;
    public static int PANEL_BG_SOFT = 0x66131313;
    public static int WIDGET_PANEL_BG = 0x90131313;
    public static int WIDGET_PANEL_BG_SOFT = 0x66131313;
    public static int ACCENT_LINE = 0xA8D0D0D0;
    public static int WIDGET_ACCENT_LINE = 0xA8D0D0D0;
    public static int TITLE = 0xFFE4E4E4;
    public static int TEXT_PRIMARY = 0xFFD8D8D8;
    public static int TEXT_SECONDARY = 0xFFBBBBBB;
    public static int TEXT_MUTED = 0xFF9A9A9A;
    public static int TEXT_SOFT = 0xFFEDEDED;
    public static int TEXT_ACCENT = 0xFFE6DDD0;
    public static int STATUS_INFO = 0xFFD0D0D0;
    public static int STATUS_SUCCESS = 0xFFD6E6D6;
    public static int STATUS_WARNING = 0xFFE6DCC8;
    public static int STATUS_ERROR = 0xFFE3C7C7;
    public static int HARD_ACCENT = 0xFFEB4242;
    public static int BAR_BG = 0x66000000;
    public static int BAR_FILL = 0xD8D6D6D6;
    public static int OUTLINE_ACTIVE = 0xFFFFFFFF;
    public static int OUTLINE_SOFT = 0xFFEEEEEE;
    public static int TOOLTIP_BG = 0xA0141414;
    public static int TOOLTIP_TEXT = 0xFFF2F2F2;
    public static int GRID_LINE = 0x33404A52;

    private WidgetTheme() {
    }

    public static void syncConfiguredTheme() {
        ThemeConfig config = ConfigManager.get() != null ? ConfigManager.get().theme : null;
        ThemePreset preset = config != null && config.preset != null ? config.preset : ThemePreset.FROG;

        switch (preset) {
            case MINT -> applyPalette(
                    0x12201B,
                    0x1A2C25,
                    0x9FD7C4,
                    0xF0FFF8,
                    0xDCEFE7,
                    0xB7CEC3,
                    0x8EA69B,
                    0xF6FFFB,
                    0xBEEBDD,
                    0xC8E8DC,
                    0xD7F1DE,
                    0xF0E3BF,
                    0xF2D3D0,
                    0x08110E,
                    0x7CCDB1
            );
            case EMBER -> applyPalette(
                    0x231716,
                    0x332120,
                    0xD9A07A,
                    0xFFF2EB,
                    0xF0D6C9,
                    0xD5B1A2,
                    0xA7867C,
                    0xFFF7F2,
                    0xFFD0B5,
                    0xF2D9C6,
                    0xE3C6A7,
                    0xF0D6A5,
                    0xE5B6B0,
                    0x140909,
                    0xD78A5F
            );
            case OCEAN -> applyPalette(
                    0x111A25,
                    0x192636,
                    0x8FB7D9,
                    0xEEF7FF,
                    0xD7E6F2,
                    0xADC3D5,
                    0x8096A8,
                    0xF5FBFF,
                    0xB9D9F3,
                    0xC9DCEF,
                    0xCBE8D8,
                    0xE7DEBC,
                    0xE2C8CE,
                    0x07111A,
                    0x76A8D4
            );
            case CUSTOM -> {
                int accentRgb = config != null
                        ? rgb(config.customAccentRed, config.customAccentGreen, config.customAccentBlue)
                        : ThemePreset.CUSTOM.getAccentRgb();
                applyCustomPalette(accentRgb);
            }
            case FROG -> applyPalette(
                    0x131313,
                    0x1B1B1B,
                    0xD0D0D0,
                    0xE4E4E4,
                    0xD8D8D8,
                    0xBBBBBB,
                    0x9A9A9A,
                    0xEDEDED,
                    0xE6DDD0,
                    0xD8D8D8,
                    0xD7E3D7,
                    0xE3DBC9,
                    0xE1CFCF,
                    0x000000,
                    0xD6D6D6
            );
        }

        // Hard accent is preset-independent (a user-set critical colour), applied after the palette.
        if (config != null) {
            HARD_ACCENT = 0xFF000000 | rgb(config.hardAccentRed, config.hardAccentGreen, config.hardAccentBlue);
        }
    }

    private static int rgb(int red, int green, int blue) {
        return (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int lighten(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = clamp(Math.round(r + (255 - r) * amount));
        g = clamp(Math.round(g + (255 - g) * amount));
        b = clamp(Math.round(b + (255 - b) * amount));
        return (r << 16) | (g << 8) | b;
    }

    private static int darken(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = clamp(Math.round(r * (1.0f - amount)));
        g = clamp(Math.round(g * (1.0f - amount)));
        b = clamp(Math.round(b * (1.0f - amount)));
        return (r << 16) | (g << 8) | b;
    }

    private static void applyPalette(
            int panelBgRgb,
            int panelSoftRgb,
            int accentRgb,
            int titleRgb,
            int primaryRgb,
            int secondaryRgb,
            int mutedRgb,
            int softRgb,
            int accentTextRgb,
            int statusInfoRgb,
            int statusSuccessRgb,
            int statusWarningRgb,
            int statusErrorRgb,
            int barBgRgb,
            int barFillRgb
    ) {
        PANEL_BG = 0x90000000 | panelBgRgb;
        PANEL_BG_SOFT = 0x7A000000 | panelSoftRgb;
        ACCENT_LINE = 0xC0000000 | accentRgb;
        int opacityPercent = getWidgetBackgroundOpacityPercent();
        WIDGET_PANEL_BG = withOpacityPercent(PANEL_BG, opacityPercent);
        WIDGET_PANEL_BG_SOFT = withOpacityPercent(PANEL_BG_SOFT, opacityPercent);
        WIDGET_ACCENT_LINE = withOpacityPercent(ACCENT_LINE, opacityPercent);
        TITLE = 0xFF000000 | titleRgb;
        TEXT_PRIMARY = 0xFF000000 | primaryRgb;
        TEXT_SECONDARY = 0xFF000000 | secondaryRgb;
        TEXT_MUTED = 0xFF000000 | mutedRgb;
        TEXT_SOFT = 0xFF000000 | softRgb;
        TEXT_ACCENT = 0xFF000000 | accentTextRgb;
        STATUS_INFO = 0xFF000000 | statusInfoRgb;
        STATUS_SUCCESS = 0xFF000000 | statusSuccessRgb;
        STATUS_WARNING = 0xFF000000 | statusWarningRgb;
        STATUS_ERROR = 0xFF000000 | statusErrorRgb;
        BAR_BG = 0xA0000000 | barBgRgb;
        BAR_FILL = 0xE0000000 | barFillRgb;
        OUTLINE_ACTIVE = withAlpha(TITLE, 0xFF);
        OUTLINE_SOFT = withAlpha(TEXT_SOFT, 0xFF);
        TOOLTIP_BG = withAlpha(PANEL_BG, 0xA0);
        TOOLTIP_TEXT = withAlpha(TEXT_SOFT, 0xFF);
        GRID_LINE = withAlpha(TEXT_MUTED, 0x33);
    }

    private static void applyCustomPalette(int accentRgb) {
        int panelBgRgb = darken(accentRgb, 0.88f);
        int panelSoftRgb = darken(accentRgb, 0.80f);
        int titleRgb = lighten(accentRgb, 0.78f);
        int primaryRgb = lighten(accentRgb, 0.66f);
        int secondaryRgb = lighten(accentRgb, 0.46f);
        int mutedRgb = lighten(accentRgb, 0.24f);
        int softRgb = lighten(accentRgb, 0.86f);
        int accentTextRgb = lighten(accentRgb, 0.58f);
        int statusInfoRgb = lighten(accentRgb, 0.58f);
        int statusSuccessRgb = lighten(accentRgb, 0.70f);
        int statusWarningRgb = lighten(accentRgb, 0.50f);
        int statusErrorRgb = lighten(accentRgb, 0.38f);
        int barBgRgb = darken(accentRgb, 0.92f);
        int barFillRgb = lighten(accentRgb, 0.12f);

        applyPalette(
                panelBgRgb,
                panelSoftRgb,
                accentRgb,
                titleRgb,
                primaryRgb,
                secondaryRgb,
                mutedRgb,
                softRgb,
                accentTextRgb,
                statusInfoRgb,
                statusSuccessRgb,
                statusWarningRgb,
                statusErrorRgb,
                barBgRgb,
                barFillRgb
        );
    }

    private static int getWidgetBackgroundOpacityPercent() {
        ThemeConfig config = ConfigManager.get() != null ? ConfigManager.get().theme : null;
        if (config == null) {
            return 100;
        }

        return Math.max(0, Math.min(100, config.widgetBackgroundOpacityPercent));
    }

    private static int withOpacityPercent(int argb, int opacityPercent) {
        int alpha = (argb >>> 24) & 0xFF;
        int scaledAlpha = Math.round(alpha * (opacityPercent / 100.0f));
        return withAlpha(argb, scaledAlpha);
    }

    public static int withAlpha(int argb, int alpha) {
        return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }
}
