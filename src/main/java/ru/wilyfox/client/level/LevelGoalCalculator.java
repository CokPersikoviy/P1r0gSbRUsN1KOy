package ru.wilyfox.client.level;

import ru.wilyfox.client.calculator.LevelCalculator;

/** Target blocks are an absolute requirement, whereas money is the sum of unbought levels. */
public final class LevelGoalCalculator {
    public record Goal(int target, int levelsLeft, long moneyRequired, long blocksRequired,
                       double moneyLeft, long blocksLeft, double progress, boolean reached, boolean ready) {}
    private LevelGoalCalculator() {}
    public static Goal calculate(LevelProgressStore.LevelProgressSnapshot current, int target,
                                 LevelCalculator.Server server, LevelCalculator.Discount discount) {
        if (!current.available() || !Double.isFinite(current.money()) || current.level() < 1 || current.level() > LevelCalculator.MAX_LEVEL
                || target < 1 || target > LevelCalculator.MAX_LEVEL) return null;
        if (current.level() >= target) return new Goal(target, 0, 0, 0, 0, 0, 1, true, true);
        var required = LevelCalculator.calculate(current.level(), target, server, discount);
        double moneyLeft = Math.max(0, required.money() - current.money());
        long blocksLeft = Math.max(0, required.blocks() - current.blocks());
        double moneyProgress = required.money() == 0 ? 1 : Math.clamp(current.money() / required.money(), 0, 1);
        double blocksProgress = required.blocks() == 0 ? 1 : Math.clamp(current.blocks() / (double)required.blocks(), 0, 1);
        return new Goal(target, required.levels(), required.money(), required.blocks(), moneyLeft, blocksLeft,
                (moneyProgress + blocksProgress) / 2, false, moneyLeft == 0 && blocksLeft == 0);
    }
}
