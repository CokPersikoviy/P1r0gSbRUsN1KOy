package ru.wilyfox.client.hud.config;

import ru.wilyfox.client.calculator.LevelCalculator;

public final class LevelGoalConfig {
    public enum ServerMode { AUTO, EVO, EVO_FAST }
    public int targetLevel;
    public ServerMode server = ServerMode.AUTO;
    public LevelCalculator.Discount discount = LevelCalculator.Discount.NONE;
    public boolean showMoney = true, showBlocks = true, showBar = true, notifications = true;
    public void sanitize() {
        targetLevel = Math.clamp(targetLevel, 0, LevelCalculator.MAX_LEVEL);
        if (server == null) server = ServerMode.AUTO;
        if (discount == null) discount = LevelCalculator.Discount.NONE;
        if (!LevelCalculator.discounts(targetLevel).contains(discount)) discount = LevelCalculator.Discount.NONE;
    }
}
