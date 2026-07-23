package ru.wilyfox.client.hud.config;

import com.google.gson.annotations.SerializedName;

public enum ThemePreset {
    @SerializedName(value = "LINGONBERRY_PIE", alternate = "FROG")
    LINGONBERRY_PIE("\u041F\u0438\u0440\u043E\u0433 \u0441 \u0411\u0440\u0443\u0441\u043D\u0438\u043A\u043E\u0439", 0x92140C, 0x1C0B19),
    @SerializedName(value = "WILD_FOX", alternate = "MINT")
    WILD_FOX("\u0414\u0438\u043A\u0438\u0439 \u041B\u0438\u0441", 0xF18805, 0x0C0C08),
    @SerializedName(value = "SWAMP_FROG", alternate = "EMBER")
    SWAMP_FROG("\u0411\u043E\u043B\u043E\u0442\u043D\u0430\u044F \u0416\u0430\u0431\u0430", 0x06D6A0, 0x2E2F2F),
    @SerializedName(value = "PICK_ME", alternate = "OCEAN")
    PICK_ME("PickMe", 0xFF2C55, 0xFEF5EF),
    CUSTOM("Custom", 0xD0D0D0, 0x9FD7C4);

    private final String title;
    private final int primaryRgb;
    private final int secondaryRgb;

    ThemePreset(String title, int primaryRgb, int secondaryRgb) {
        this.title = title;
        this.primaryRgb = primaryRgb;
        this.secondaryRgb = secondaryRgb;
    }

    public String getTitle() {
        return title;
    }

    public int getPrimaryRgb() {
        return primaryRgb;
    }

    public int getSecondaryRgb() {
        return secondaryRgb;
    }
}
