package ru.wilyfox.client.statistic;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.config.MiningStatisticsConfig;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class MiningStatisticsStoreTest {
    @Test void accountsForPerBlockIncomeAndActiveTimeWithoutCountingRepeatedActionBars() {
        var clock = new AtomicLong(); var config = new MiningStatisticsConfig();
        var store = new MiningStatisticsStore(clock::get);
        store.actionBar("+1,5M", config); store.actionBar("+1,5M", config);
        store.recordBreak(config); store.recordBreak(config);
        clock.set(2_000_000_000L);
        var result = store.snapshot(config);
        assertEquals(2, result.blocks()); assertEquals(3_000_000, result.money());
        assertEquals(2, result.seconds()); assertEquals(3600, result.blocksPerHour());
        assertEquals(5_400_000_000.0, result.moneyPerHour());
    }
    @Test void idlePauseStopsExactlyAtTheTimeoutAndResumesOnActivity() {
        var clock = new AtomicLong(); var config = new MiningStatisticsConfig();
        var store = new MiningStatisticsStore(clock::get);
        store.recordBreak(config);
        clock.set(60_000_000_000L);
        assertEquals(10, store.snapshot(config).seconds());
        assertEquals(MiningStatisticsStore.State.AUTO_PAUSED, store.snapshot(config).state());
        store.recordBreak(config); clock.set(62_000_000_000L);
        assertEquals(12, store.snapshot(config).seconds()); assertEquals(2, store.snapshot(config).blocks());
    }
    @Test void manualPauseNeverResumesFromChatOrBlockEventsAndDoesNotCountTheirRewards() {
        var clock = new AtomicLong(); var config = new MiningStatisticsConfig();
        var store = new MiningStatisticsStore(clock::get);
        store.recordBreak(config); clock.set(1_000_000_000L); store.togglePause(config);
        clock.set(30_000_000_000L); store.recordBreak(config); store.message("Вы нашли шард! x3", config);
        assertEquals(1, store.snapshot(config).blocks()); assertEquals(0, store.snapshot(config).shards());
        assertEquals(1, store.snapshot(config).seconds());
        store.togglePause(config); store.message("Вы нашли шард! x3", config); clock.set(32_000_000_000L);
        assertEquals(3, store.snapshot(config).shards()); assertEquals(3, store.snapshot(config).seconds());
    }
    @Test void sourceSelectionAndBlocksOnlyModeDoNotDisableBlockAndShardCounting() {
        var clock = new AtomicLong(); var config = new MiningStatisticsConfig();
        var store = new MiningStatisticsStore(clock::get);
        config.sources.remove(MiningStatisticsConfig.Source.BARRELS);
        store.message("Фиолетовая бочка принесла 12.5B", config);
        assertEquals(0, store.snapshot(config).money());
        store.message("Руна дала 12.5B", config);
        assertEquals(12_500_000_000.0, store.snapshot(config).money());
        config.trackMoney = false;
        store.message("Руна дала 12.5B", config); store.recordBreak(config); store.message("Вы нашли шард!", config);
        assertEquals(12_500_000_000.0, store.snapshot(config).money());
        assertEquals(1, store.snapshot(config).blocks()); assertEquals(1, store.snapshot(config).shards());
    }
    @Test void bulkRewardIsNotReusedAsThePerBlockPriceAndStalePricesExpire() {
        var clock = new AtomicLong(); var config = new MiningStatisticsConfig();
        var store = new MiningStatisticsStore(clock::get);
        store.actionBar("+1K", config); store.recordBreak(config);
        store.message("Вы взорвали 100 блоков и получили 10M", config);
        store.actionBar("+10M", config); store.recordBreak(config);
        assertEquals(10_002_000, store.snapshot(config).money());
        clock.set(11_000_000_000L); store.recordBreak(config);
        assertEquals(10_002_000, store.snapshot(config).money());
    }
    @Test void disconnectsAndResetHaveNoHiddenElapsedTimeOrInvalidRates() {
        var clock = new AtomicLong(); var config = new MiningStatisticsConfig(); config.autoPause = false;
        var store = new MiningStatisticsStore(clock::get);
        assertEquals(0, store.snapshot(config).blocksPerHour());
        store.recordBreak(config); clock.set(2_000_000_000L); store.suspend(config);
        clock.set(200_000_000_000L); assertEquals(2, store.snapshot(config).seconds());
        store.recordBreak(config); clock.set(201_000_000_000L); assertEquals(3, store.snapshot(config).seconds());
        store.reset(); var result = store.snapshot(config);
        assertEquals(0, result.blocks()); assertEquals(0, result.money()); assertEquals(0, result.shards());
        assertEquals(0, result.seconds()); assertEquals(MiningStatisticsStore.State.WAITING, result.state());
    }
}
