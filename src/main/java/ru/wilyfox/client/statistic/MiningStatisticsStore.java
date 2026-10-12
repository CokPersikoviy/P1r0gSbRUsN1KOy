package ru.wilyfox.client.statistic;

import ru.wilyfox.client.hud.config.MiningStatisticsConfig;
import java.util.function.LongSupplier;

/** One in-memory mining session. Monotonic time excludes manual/idle pauses and disconnects. */
public final class MiningStatisticsStore {
    public enum State { WAITING, RUNNING, AUTO_PAUSED, MANUAL_PAUSED }
    private final LongSupplier clock;
    private State state = State.WAITING;
    private long blocks, shards, activeNanos, updatedAt, lastActivity, priceAt;
    private double money, blockPrice;
    private long ignorePriceUntil;
    public MiningStatisticsStore() { this(System::nanoTime); }
    public MiningStatisticsStore(LongSupplier clock) { this.clock = clock; reset(); }
    public void reset() {
        blocks = shards = activeNanos = 0; money = blockPrice = 0;
        updatedAt = lastActivity = clock.getAsLong(); priceAt = ignorePriceUntil = Long.MIN_VALUE;
        state = State.WAITING;
    }
    public void advance(MiningStatisticsConfig config) {
        long now = clock.getAsLong();
        if (state == State.RUNNING) {
            long end = now;
            if (config.autoPause && now - lastActivity >= config.idleSeconds * 1_000_000_000L) {
                end = lastActivity + config.idleSeconds * 1_000_000_000L;
                state = State.AUTO_PAUSED;
            }
            activeNanos += Math.max(0, end - updatedAt);
        }
        updatedAt = now;
    }
    private boolean activity(MiningStatisticsConfig config) {
        advance(config);
        if (state == State.MANUAL_PAUSED) return false;
        lastActivity = updatedAt; state = State.RUNNING;
        return true;
    }
    public void recordBreak(MiningStatisticsConfig config) {
        if (!activity(config)) return;
        if (blocks < Long.MAX_VALUE) blocks++;
        long age = updatedAt - priceAt;
        if (config.trackMoney && config.sources.contains(MiningStatisticsConfig.Source.BLOCKS)
                && blockPrice > 0 && age >= 0 && age <= 10_000_000_000L) addMoney(blockPrice);
    }
    public void actionBar(String text, MiningStatisticsConfig config) {
        if (!config.trackMoney || state == State.MANUAL_PAUSED || clock.getAsLong() < ignorePriceUntil) return;
        double price = MiningRewardParser.actionBarPrice(text);
        if (price > 0) { blockPrice = price; priceAt = clock.getAsLong(); }
    }
    public void message(String text, MiningStatisticsConfig config) {
        var reward = MiningRewardParser.reward(text);
        if (reward == null || reward.money() > 0 && (!config.trackMoney || !config.sources.contains(reward.source()))) return;
        if (!activity(config)) return;
        if (reward.shards() > 0) shards = shards > Long.MAX_VALUE - reward.shards() ? Long.MAX_VALUE : shards + reward.shards();
        if (reward.money() > 0) {
            addMoney(reward.money());
            // Bulk rewards also flash in the action bar. They are not a new per-block price.
            ignorePriceUntil = updatedAt + 1_000_000_000L;
        }
    }
    private void addMoney(double value) { money = Math.min(1e30, money + value); }
    public void togglePause(MiningStatisticsConfig config) {
        advance(config);
        if (state == State.MANUAL_PAUSED) { state = State.RUNNING; lastActivity = updatedAt; }
        else { state = State.MANUAL_PAUSED; blockPrice = 0; }
    }
    public void suspend(MiningStatisticsConfig config) {
        advance(config);
        if (state == State.RUNNING) state = State.AUTO_PAUSED;
        blockPrice = 0;
    }
    public Snapshot snapshot(MiningStatisticsConfig config) {
        advance(config);
        double hours = activeNanos / 3_600_000_000_000.0;
        return new Snapshot(blocks, money, shards, activeNanos / 1_000_000_000L, state,
                hours > 0 ? blocks / hours : 0, hours > 0 ? money / hours : 0, hours > 0 ? shards / hours : 0);
    }
    public record Snapshot(long blocks, double money, long shards, long seconds, State state,
                           double blocksPerHour, double moneyPerHour, double shardsPerHour) {}
}
