package ru.wilyfox.client.highlight;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NearbyMineStateTest {
    private static final NearbyMineState.Point PLAYER=new NearbyMineState.Point(0,0,0);
    @AfterEach void reset(){NearbyMineState.clear();}
    @Test void excludesDistantObjectsAndReturnsNearestWithinTheExistingVerticalLimit() {
        NearbyMineState.entities(List.of(new NearbyMineState.Point(5,0,0),new NearbyMineState.Point(12,0,0),new NearbyMineState.Point(0,21,0)),List.of());
        var close=NearbyMineState.count(NearbyMineState.Kind.WORMS,PLAYER,10);
        assertEquals(1,close.count());assertEquals(5,close.nearestDistance());
        assertEquals(2,NearbyMineState.count(NearbyMineState.Kind.WORMS,PLAYER,32).count());
    }
    @Test void deduplicatesBlockAndCrystalRepresentationsAndClearsUnloadedResults() {
        var p=new NearbyMineState.Point(3,0,0);NearbyMineState.barrels(List.of(p,p));NearbyMineState.entities(List.of(),List.of(p,p));
        assertEquals(1,NearbyMineState.count(NearbyMineState.Kind.BARRELS,PLAYER,32).count());
        assertEquals(1,NearbyMineState.count(NearbyMineState.Kind.CRYSTALS,PLAYER,32).count());
        NearbyMineState.clear();
        for(var kind:NearbyMineState.Kind.values()){var result=NearbyMineState.count(kind,PLAYER,32);assertEquals(0,result.count());assertNull(result.nearest());}
    }
    @Test void movingAwayDoesNotKeepAStaleCounterAndKindsRemainIndependent() {
        var p=new NearbyMineState.Point(2,0,0);NearbyMineState.barrels(List.of(p));
        assertEquals(0,NearbyMineState.count(NearbyMineState.Kind.WORMS,PLAYER,32).count());
        assertEquals(0,NearbyMineState.count(NearbyMineState.Kind.BARRELS,new NearbyMineState.Point(100,0,0),32).count());
        NearbyMineState.barrels(List.of());assertEquals(0,NearbyMineState.count(NearbyMineState.Kind.BARRELS,PLAYER,32).count());
    }
}
