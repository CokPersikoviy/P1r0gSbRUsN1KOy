package ru.wilyfox.client.level;

public final class LevelProgressStore {
    private volatile int level;
    private volatile int blocks;
    private volatile double money;
    private volatile int requiredBlocks;
    private volatile double requiredMoney;
    private volatile boolean maxLevel;
    private volatile boolean hasCurrentData;
    private volatile boolean hasRequirementData;
    private volatile long levelInfoRevision;
    private volatile boolean lastLevelInfoCompleted;
    private volatile long lastCompletionRevision;

    public void updateCurrent(Integer level, Integer blocks, Double money) {
        if (level == null && blocks == null && money == null) {
            return;
        }

        if (level != null) {
            this.level = Math.max(0, level);
        }
        if (blocks != null) {
            this.blocks = Math.max(0, blocks);
        }
        if (money != null) {
            this.money = Math.max(0.0D, money);
        }
        this.hasCurrentData = true;
    }

    public void updateRequirements(int requiredBlocks, double requiredMoney, boolean maxLevel) {
        int sanitizedBlocks = Math.max(0, requiredBlocks);
        double sanitizedMoney = Math.max(0.0D, requiredMoney);

        this.requiredBlocks = sanitizedBlocks;
        this.requiredMoney = sanitizedMoney;
        this.maxLevel = maxLevel;
        this.hasRequirementData = true;
    }

    public void updateFromLevelInfo(
            int level,
            int blocks,
            double money,
            int requiredBlocks,
            double requiredMoney,
            boolean maxLevel
    ) {
        this.level = Math.max(0, level);
        this.blocks = Math.max(0, blocks);
        this.money = Math.max(0.0D, money);
        this.requiredBlocks = Math.max(0, requiredBlocks);
        this.requiredMoney = Math.max(0.0D, requiredMoney);
        this.maxLevel = maxLevel;
        this.hasCurrentData = true;
        this.hasRequirementData = true;
        this.levelInfoRevision++;
        boolean completed = !maxLevel
                && this.blocks >= this.requiredBlocks
                && this.money >= this.requiredMoney;
        if (completed && !lastLevelInfoCompleted) {
            this.lastCompletionRevision = this.levelInfoRevision;
        }
        this.lastLevelInfoCompleted = completed;
    }

    public LevelProgressSnapshot getSnapshot() {
        return new LevelProgressSnapshot(
                level,
                blocks,
                money,
                requiredBlocks,
                requiredMoney,
                maxLevel,
                hasCurrentData && hasRequirementData,
                levelInfoRevision,
                lastCompletionRevision
        );
    }

    public void clear() {
        level = 0;
        blocks = 0;
        money = 0.0D;
        requiredBlocks = 0;
        requiredMoney = 0.0D;
        maxLevel = true;
        hasCurrentData = false;
        hasRequirementData = false;
        levelInfoRevision = 0L;
        lastLevelInfoCompleted = false;
        lastCompletionRevision = 0L;
    }

    public record LevelProgressSnapshot(
            int level,
            int blocks,
            double money,
            int requiredBlocks,
            double requiredMoney,
            boolean maxLevel,
            boolean available,
            long levelInfoRevision,
            long lastCompletionRevision
    ) {
        public boolean completed() {
            return available
                    && !maxLevel
                    && blocks >= requiredBlocks
                    && money >= requiredMoney;
        }

        public double progress() {
            if (maxLevel) {
                return 1.0D;
            }

            double blocksProgress = requiredBlocks <= 0 ? 1.0D : Math.min(1.0D, blocks / (double) requiredBlocks);
            double moneyProgress = requiredMoney <= 0.0D ? 1.0D : Math.min(1.0D, money / requiredMoney);
            return (blocksProgress + moneyProgress) / 2.0D;
        }
    }
}
