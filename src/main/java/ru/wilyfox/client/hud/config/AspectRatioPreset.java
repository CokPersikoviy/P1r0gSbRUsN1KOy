package ru.wilyfox.client.hud.config;

public enum AspectRatioPreset {
    OFF("Off", 0), R4_3("4:3", 4.0 / 3), R5_4("5:4", 5.0 / 4),
    R3_2("3:2", 1.5), R16_10("16:10", 1.6), R16_9("16:9", 16.0 / 9),
    R21_9("21:9", 21.0 / 9), R1_1("1:1", 1), CUSTOM("Custom", 0);

    public final String title;
    public final double ratio;
    AspectRatioPreset(String title, double ratio) { this.title = title; this.ratio = ratio; }
}
