package ru.wilyfox.client.hud.config;

public class VisualsConfig {
    public AspectRatioPreset aspectRatio = AspectRatioPreset.OFF;
    public int aspectWidth = 1440;
    public int aspectHeight = 1080;
    public boolean stretchHand = true;
    public boolean blockOutline = false;
    public int blockOutlineColor = 0xFF43D5FF;
    public double blockOutlineWidth = 2;
    public boolean skyColorEnabled = false;
    public int skyColor = 0xFF78A7FF;
    public boolean fogColorEnabled = false;
    public int fogColor = 0xFFC0D8FF;

    public int formatRevision;
    public Crosshair crosshair = new Crosshair();
    public Hitboxes hitboxes = new Hitboxes();
    public Hand hand = new Hand();
    public Camera camera = new Camera();
    public Tab tab = new Tab();
    public LineStyle blockStyle = LineStyle.NORMAL;
    public OutlineMode blockMode = OutlineMode.EXACT;
    public boolean blockFill, blockThroughWalls, blockRainbow;
    public int blockFillColor = 0x2743D5FF;
    public int blockAnimation, blockLinger, blockFade;
    public int blockRainbowSpeed = 5;
    public boolean compactReload;
    public boolean cloudColorEnabled, brightnessEnabled, skyLightEnabled, blockLightEnabled, timeEnabled;
    public int cloudColor = 0xCCD8E8FF, brightness = 100, skyLightColor = 0xFFFFFFFF, blockLightColor = 0xFFFFD8A0;
    public int time = 6000, shadowStrength = 100, shadowRadius = 100;
    public FogMode fogMode = FogMode.DEFAULT;
    public double fogDistance = 2;
    public boolean fluidFog = true;
    public Weather weather = Weather.SERVER;

    public enum LineStyle { NORMAL, NEON, DASHED, CORNERS }
    public enum OutlineMode { EXACT, BOX }
    public enum FogMode { DEFAULT, CUSTOM, NONE }
    public enum Weather { SERVER, CLEAR, RAIN, THUNDER }
    public enum CrosshairStyle { CROSS, X, CIRCLE, SQUARE, DOT }
    public enum PixelScale { SCREEN, HEIGHT }
    public enum DrawColor { OFF, FULL, SMOOTH }
    public enum Indicator { OFF, BAR, RING }
    public enum Targets { ALL, LIVING, PLAYERS }
    public enum LookMode { HOLD, TOGGLE }

    public static class Crosshair {
        public boolean enabled, dynamic, invert, targetColorEnabled, thirdPerson, splitLength, dot, dotColorEnabled, drawEnabled;
        public boolean top = true, bottom = true, left = true, right = true, outline = true, drawGap = true, drawFlash = true;
        public CrosshairStyle style = CrosshairStyle.CROSS;
        public PixelScale scale = PixelScale.SCREEN;
        public DrawColor drawColor = DrawColor.SMOOTH;
        public Indicator indicator = Indicator.OFF;
        public int referenceHeight = 1080, color = 0xFF55FF55, targetColor = 0xFFFF5555, dotColor = 0xFFFFFFFF;
        public int outlineColor = 0xC0000000, fullColor = 0xFFFFD84A, indicatorColor = 0xFFFFFFFF;
        public double length = 6, verticalLength = 6, thickness = 2, gap = 3, dotSize = 2, outlineThickness = 1;
        public double runSpread = 6, jumpSpread = 8, attackSpread = 4, recovery = 12;
        public double drawGapFrom = 4, drawGapTo = 0, indicatorSize = 10, indicatorThickness = 2;
        void sanitize() {
            if (style == null) style = CrosshairStyle.CROSS;
            if (scale == null) scale = PixelScale.SCREEN;
            if (drawColor == null) drawColor = DrawColor.SMOOTH;
            if (indicator == null) indicator = Indicator.OFF;
            referenceHeight = Math.clamp(referenceHeight, 240, 8192);
            length = number(length, 0, 64, 6); verticalLength = number(verticalLength, 0, 64, 6);
            thickness = number(thickness, 1, 16, 2); gap = number(gap, 0, 64, 3);
            dotSize = number(dotSize, 1, 32, 2); outlineThickness = number(outlineThickness, 1, 8, 1);
            runSpread = number(runSpread, 0, 64, 6); jumpSpread = number(jumpSpread, 0, 64, 8);
            attackSpread = number(attackSpread, 0, 64, 4); recovery = number(recovery, 1, 40, 12);
            drawGapFrom = number(drawGapFrom, -64, 64, 4); drawGapTo = number(drawGapTo, -64, 64, 0);
            indicatorSize = number(indicatorSize, 1, 64, 10); indicatorThickness = number(indicatorThickness, 1, 16, 2);
        }
    }
    public static class Hitboxes {
        public boolean show, custom, self, eyeLine, fill;
        public boolean lookArrow = true, hover = true, hit = true;
        public int modifiers, key = -1, color = 0xFFFFFFFF, eyeColor = 0xFFFF5555, lookColor = 0xFF5555FF;
        public int hoverColor = 0xFF55FFFF, hitColor = 0xFFFF557F, fillColor = 0x31FFFFFF;
        public double width = 2.5;
        public Targets targets = Targets.ALL;
        public LineStyle style = LineStyle.NORMAL;
        void sanitize() {
            modifiers &= 7; key = bind(key); if (targets == null) targets = Targets.ALL; if (style == null) style = LineStyle.NORMAL;
            width = number(width, 1, 8, 2.5);
        }
    }
    public static class Transform {
        public double x, y, z, scale = 1;
        public int pitch, yaw, roll;
        public boolean identity() { return x == 0 && y == 0 && z == 0 && pitch == 0 && yaw == 0 && roll == 0 && scale == 1; }
        void sanitize() {
            x = number(x, -3, 3, 0); y = number(y, -3, 3, 0); z = number(z, -3, 3, 0);
            scale = number(scale, 0.1, 3, 1);
            pitch = Math.clamp(pitch, -180, 180); yaw = Math.clamp(yaw, -180, 180); roll = Math.clamp(roll, -180, 180);
        }
    }
    public static class Hand {
        public boolean enabled, fovEnabled;
        public boolean mirror = true, equipAnimation = true;
        public int fov = 70;
        public double swingSpeed = 1;
        public Transform main = new Transform(), off = new Transform();
        void sanitize() {
            if (main == null) main = new Transform(); if (off == null) off = new Transform();
            main.sanitize(); off.sanitize(); fov = Math.clamp(fov, 30, 130); swingSpeed = number(swingSpeed, 0.1, 5, 1);
        }
    }
    public static class Camera {
        public boolean enabled, distanceEnabled, invertPitch, frontView;
        public int key = -1, modifiers;
        public LookMode mode = LookMode.HOLD;
        public double distance = 4, thirdPersonDistance = 4;
        void sanitize() {
            modifiers &= 7; key = bind(key); if (mode == null) mode = LookMode.HOLD;
            distance = number(distance, 0.5, 20, 4); thirdPersonDistance = number(thirdPersonDistance, 0.5, 20, 4);
        }
    }
    public static class Tab {
        public boolean enabled;
        public boolean interactive = true, scrollPaging = true, pageHint = true;
        public int cursorModifiers, rows = 20, columns = 4, columnWidth, highlight = 0x5643D5FF, cursorKey = 1002;
        void sanitize() {
            rows = Math.clamp(rows, 1, 40); columns = Math.clamp(columns, 1, 8); columnWidth = Math.clamp(columnWidth, 0, 600);
            cursorModifiers &= 7; cursorKey = bind(cursorKey);
        }
    }
    private static int bind(int key) { return key >= 32 && key <= 348 || key >= 1000 && key <= 1007 ? key : -1; }
    private static double number(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
    public boolean advancedOutline() {
        return blockOutline && (blockStyle != LineStyle.NORMAL || blockMode != OutlineMode.EXACT || blockFill
                || blockThroughWalls || blockRainbow || blockAnimation > 0 || blockLinger > 0 || blockFade > 0);
    }

    public double ratio() {
        if (aspectRatio == null || aspectRatio == AspectRatioPreset.OFF) return 0;
        if (aspectRatio != AspectRatioPreset.CUSTOM) return aspectRatio.ratio;
        return Math.clamp(Math.max(1, aspectWidth) / (double) Math.max(1, aspectHeight), 0.25, 4.0);
    }

    public void sanitize() {
        if (formatRevision < 1) {
            // The earlier mini-Visuals picker saved RGB without an alpha byte.
            blockOutlineColor |= 0xFF000000; skyColor |= 0xFF000000; fogColor |= 0xFF000000;
            formatRevision = 1;
        }
        if (aspectRatio == null) aspectRatio = AspectRatioPreset.OFF;
        aspectWidth = Math.clamp(aspectWidth, 1, 8192);
        aspectHeight = Math.clamp(aspectHeight, 1, 8192);
        blockOutlineWidth = number(blockOutlineWidth, 1, 8, 2);
        if (crosshair == null) crosshair = new Crosshair(); if (hitboxes == null) hitboxes = new Hitboxes();
        if (hand == null) hand = new Hand(); if (camera == null) camera = new Camera(); if (tab == null) tab = new Tab();
        crosshair.sanitize(); hitboxes.sanitize(); hand.sanitize(); camera.sanitize(); tab.sanitize();
        if (blockStyle == null) blockStyle = LineStyle.NORMAL; if (blockMode == null) blockMode = OutlineMode.EXACT;
        if (fogMode == null) fogMode = FogMode.DEFAULT; if (weather == null) weather = Weather.SERVER;
        blockAnimation = Math.clamp(blockAnimation, 0, 1000); blockLinger = Math.clamp(blockLinger, 0, 2000);
        blockFade = Math.clamp(blockFade, 0, 1000); blockRainbowSpeed = Math.clamp(blockRainbowSpeed, 1, 20);
        brightness = Math.clamp(brightness, 0, 200); time = Math.clamp(time, 0, 23999);
        shadowStrength = Math.clamp(shadowStrength, 0, 200); shadowRadius = Math.clamp(shadowRadius, 0, 200);
        fogDistance = number(fogDistance, 0.1, 10, 2);
    }
}
