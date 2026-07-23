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
        ThemePreset preset = config != null && config.preset != null
                ? config.preset
                : ThemePreset.LINGONBERRY_PIE;

        switch (preset) {
            case CUSTOM -> {
                int primaryRgb = config != null
                        ? rgb(config.customAccentRed, config.customAccentGreen, config.customAccentBlue)
                        : ThemePreset.CUSTOM.getPrimaryRgb();
                int secondaryRgb = config != null
                        ? rgb(config.customSecondaryRed, config.customSecondaryGreen, config.customSecondaryBlue)
                        : ThemePreset.CUSTOM.getSecondaryRgb();
                applyCustomPalette(primaryRgb, secondaryRgb);
            }
            case LINGONBERRY_PIE, WILD_FOX, SWAMP_FROG, PICK_ME ->
                    applyPresetPalette(preset, config != null && config.swapPresetColors);
        }

        // Hard accent is preset-independent (a user-set critical colour), applied after the palette.
        if (config != null) {
            HARD_ACCENT = 0xFF000000 | rgb(config.hardAccentRed, config.hardAccentGreen, config.hardAccentBlue);
        }
    }

    private static void applyPresetPalette(ThemePreset preset, boolean swapped) {
        int primaryRgb = swapped ? preset.getSecondaryRgb() : preset.getPrimaryRgb();
        int secondaryRgb = swapped ? preset.getPrimaryRgb() : preset.getSecondaryRgb();
        applyCustomPalette(primaryRgb, secondaryRgb);
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
                barFillRgb,
                getWidgetBackgroundOpacityPercent()
        );
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
            int barFillRgb,
            int opacityPercent
    ) {
        PANEL_BG = 0x90000000 | panelBgRgb;
        PANEL_BG_SOFT = 0x7A000000 | panelSoftRgb;
        ACCENT_LINE = 0xC0000000 | accentRgb;
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
        OUTLINE_SOFT = withAlpha(TEXT_SECONDARY, 0xFF);
        TOOLTIP_BG = withAlpha(PANEL_BG, 0xA0);
        TOOLTIP_TEXT = withAlpha(TEXT_SOFT, 0xFF);
        GRID_LINE = withAlpha(TEXT_MUTED, 0x33);
    }

    private static void applyCustomPalette(int primarySourceRgb, int secondarySourceRgb) {
        applyCustomPalette(primarySourceRgb, secondarySourceRgb, getWidgetBackgroundOpacityPercent());
    }

    static void applyCustomPalette(int primarySourceRgb, int secondarySourceRgb, int opacityPercent) {
        // Primary owns active/foreground states. Secondary owns surfaces and supporting content.
        int panelBgRgb = darken(secondarySourceRgb, 0.88f);
        int panelSoftRgb = darken(secondarySourceRgb, 0.80f);
        int titleRgb = lighten(primarySourceRgb, 0.78f);
        int primaryTextRgb = lighten(primarySourceRgb, 0.66f);
        int secondaryTextRgb = lighten(secondarySourceRgb, 0.46f);
        int mutedRgb = lighten(secondarySourceRgb, 0.24f);
        int softRgb = lighten(primarySourceRgb, 0.86f);
        int accentTextRgb = lighten(primarySourceRgb, 0.58f);
        int statusInfoRgb = lighten(primarySourceRgb, 0.58f);
        int statusSuccessRgb = lighten(primarySourceRgb, 0.70f);
        int statusWarningRgb = lighten(primarySourceRgb, 0.50f);
        int statusErrorRgb = lighten(primarySourceRgb, 0.38f);
        int barBgRgb = darken(secondarySourceRgb, 0.92f);
        int barFillRgb = lighten(primarySourceRgb, 0.12f);

        applyPalette(
                panelBgRgb,
                panelSoftRgb,
                primarySourceRgb,
                titleRgb,
                primaryTextRgb,
                secondaryTextRgb,
                mutedRgb,
                softRgb,
                accentTextRgb,
                statusInfoRgb,
                statusSuccessRgb,
                statusWarningRgb,
                statusErrorRgb,
                barBgRgb,
                barFillRgb,
                opacityPercent
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
