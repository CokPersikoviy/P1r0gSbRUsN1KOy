package ru.wilyfox.client.hud.menu;

/** Relative scrubbing in GUI pixels, retaining sub-step movement and discarding excess at limits. */
final class NumberDrag {
    private double lastX;
    private double remainder;

    void begin(double mouseX) {
        lastX = mouseX;
        remainder = 0;
    }

    int update(double mouseX, int value, int min, int max, int step, boolean fine) {
        max = Math.max(min, max);
        value = Math.max(min, Math.min(max, value));
        double movement = (mouseX - lastX) / (fine ? 40.0 : 4.0);
        lastX = mouseX;
        if (!Double.isFinite(movement)) return value;
        if ((value == max && movement > 0) || (value == min && movement < 0)) {
            remainder = 0;
            return value;
        }
        remainder += movement;
        double wholeSteps = remainder < 0 ? Math.ceil(remainder) : Math.floor(remainder);
        double candidate = value + wholeSteps * Math.max(1, step);
        int result = (int) Math.max(min, Math.min(max, candidate));
        remainder = wholeSteps != 0 && (result == min || result == max) ? 0 : remainder - wholeSteps;
        return result;
    }
}
