package ru.wilyfox.client.level;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.calculator.LevelCalculator;
import static org.junit.jupiter.api.Assertions.*;

class LevelGoalCalculatorTest {
    private static LevelProgressStore.LevelProgressSnapshot current(int level,int blocks,double money) {
        var store=new LevelProgressStore();store.updateFromLevelInfo(level,blocks,money,1,1,false);return store.getSnapshot();
    }
    @Test void subtractsActualBalanceAndLifetimeBlocksInsteadOfSubtractingThePreviousLevelTable() {
        var cost=LevelCalculator.calculate(125,150,LevelCalculator.Server.EVO,LevelCalculator.Discount.NONE);
        var result=LevelGoalCalculator.calculate(current(125,12345,70000),150,LevelCalculator.Server.EVO,LevelCalculator.Discount.NONE);
        assertEquals(cost.money()-70000,result.moneyLeft());assertEquals(Math.max(0,cost.blocks()-12345),result.blocksLeft());
        assertEquals(25,result.levelsLeft());assertFalse(result.reached());
    }
    @Test void readinessNeedsBothResourcesAndDoesNotPretendThePlayerHasBoughtTheLevels() {
        var cost=LevelCalculator.calculate(500,550,LevelCalculator.Server.EVO,LevelCalculator.Discount.MAX);
        var moneyOnly=LevelGoalCalculator.calculate(current(500,0,cost.money()),550,LevelCalculator.Server.EVO,LevelCalculator.Discount.MAX);
        assertFalse(moneyOnly.ready());assertEquals(.5,moneyOnly.progress());
        var ready=LevelGoalCalculator.calculate(current(500,(int)cost.blocks(),cost.money()+100),550,LevelCalculator.Server.EVO,LevelCalculator.Discount.MAX);
        assertTrue(ready.ready());assertFalse(ready.reached());assertEquals(0,ready.moneyLeft());assertEquals(0,ready.blocksLeft());assertEquals(1,ready.progress());
    }
    @Test void handlesReachedUnknownAndInvalidTargets() {
        assertTrue(LevelGoalCalculator.calculate(current(150,0,0),150,LevelCalculator.Server.EVO,LevelCalculator.Discount.NONE).reached());
        assertTrue(LevelGoalCalculator.calculate(current(160,0,0),150,LevelCalculator.Server.EVO,LevelCalculator.Discount.NONE).reached());
        assertNull(LevelGoalCalculator.calculate(new LevelProgressStore().getSnapshot(),150,LevelCalculator.Server.EVO,LevelCalculator.Discount.NONE));
        assertNull(LevelGoalCalculator.calculate(current(125,1,1),0,LevelCalculator.Server.EVO,LevelCalculator.Discount.NONE));
        assertNull(LevelGoalCalculator.calculate(current(125,1,1),601,LevelCalculator.Server.EVO,LevelCalculator.Discount.NONE));
    }
    @Test void sharesFastServerRoundingAndTieredDiscountsWithTheCalculator() {
        var result=LevelGoalCalculator.calculate(current(480,0,0),550,LevelCalculator.Server.EVO_FAST,LevelCalculator.Discount.MAX);
        var cost=LevelCalculator.calculate(480,550,LevelCalculator.Server.EVO_FAST,LevelCalculator.Discount.MAX);
        assertEquals(cost.blocks(),result.blocksRequired());assertEquals(cost.money(),result.moneyRequired());
        assertEquals(0,result.progress());
    }
}
