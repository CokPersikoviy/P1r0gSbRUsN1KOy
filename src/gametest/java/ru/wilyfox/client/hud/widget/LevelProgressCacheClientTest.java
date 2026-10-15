package ru.wilyfox.client.hud.widget;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.util.FormattedCharSequence;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.level.LevelProgressStore;
import java.lang.reflect.Field;

public final class LevelProgressCacheClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var store = new LevelProgressStore();
            store.updateFromLevelInfo(239, 1500, 12345, 123456, 1e12, false);
            var widget = new LevelProgressWidget(0, 0, HudLayer.CONTENT, store);
            int width = widget.getWidth();
            Object title = field(widget,"title"), blocks=field(widget,"blocksLine"), money=field(widget,"moneyLine");
            check(text(title).equals("Level 239"),"Level title changed");
            check(text(blocks).equals("Blocks: 1,500/123.46K"),"Block formatting changed: "+text(blocks));
            check(text(money).equals("Money: 12.35K/1T"),"Money formatting changed: "+text(money));
            for(int i=0;i<10000;i++) check(widget.getWidth()==width,"Width changed without a data update");
            check(title==field(widget,"title")&&blocks==field(widget,"blocksLine")&&money==field(widget,"moneyLine"),"Unchanged text was rebuilt");
            store.updateCurrent(null,9999,1e9);
            widget.getWidth();
            check(text(field(widget,"blocksLine")).equals("Blocks: 9,999/123.46K"),"Partial update left stale blocks");
            check(text(field(widget,"moneyLine")).equals("Money: 1B/1T"),"Partial update left stale money");
            store.updateFromLevelInfo(300,0,0,0,0,true);widget.getWidth();
            check(text(field(widget,"title")).equals("Level 300 · MAX")&&field(widget,"blocksLine")==null&&field(widget,"moneyLine")==null,"Max-level rows changed");
            store.clear();check(widget.getWidth()==150,"Unavailable level did not restore placeholder dimensions");
            System.out.println("Level progress: 10000 reads reused formatted text; compact numbers, partial updates, MAX and placeholder dimensions passed");
        });
    }
    private static Object field(Object target,String name) {
        try {Field f=LevelProgressWidget.class.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static String text(Object sequence) {var result=new StringBuilder();((FormattedCharSequence)sequence).accept((i,s,c)->{result.appendCodePoint(c);return true;});return result.toString();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
