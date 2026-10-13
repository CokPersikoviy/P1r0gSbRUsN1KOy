package ru.wilyfox.client.hud.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.calculator.LevelCalculator;
import static org.junit.jupiter.api.Assertions.*;

class GoalAndNearbyConfigTest {
    @Test void legacyClickerKeepsRepeatedClicksAndNewWidgetsAreNotAddedDuringMigration() {
        var c=HudConfigCodec.decode(HudConfigCodec.createGson(),JsonParser.parseString("{\"clicker\":{\"activation\":\"HOLD\"}}"));
        assertEquals(ClickerConfig.ActionMode.CLICK,c.clicker.actionMode);
        assertEquals(ClickerConfig.Activation.HOLD,c.clicker.activation);
        for(var widget:new WidgetCatalog[]{WidgetCatalog.LEVEL_GOAL,WidgetCatalog.NEARBY_WORMS,WidgetCatalog.NEARBY_BARRELS,WidgetCatalog.NEARBY_CRYSTALS})
            assertFalse(c.mainLayout.widgets.contains(widget.key()));
    }
    @Test void repairsRangesAndSharesGoalAndDetectorSettings() {
        var c=new HudConfig();c.levelGoal.targetLevel=999;c.levelGoal.discount=LevelCalculator.Discount.MAX;
        c.clicker.actionMode=ClickerConfig.ActionMode.HOLD;c.nearbyWorms.range=999;c.nearbyBarrels.range=-1;
        c.mainLayout.widgets.add(WidgetCatalog.LEVEL_GOAL.key());c.mainLayout.widgets.add(WidgetCatalog.NEARBY_WORMS.key());
        var gson=HudConfigCodec.createGson();var restored=HudConfigCodec.decode(gson,gson.toJsonTree(c));
        assertEquals(600,restored.levelGoal.targetLevel);assertEquals(32,restored.nearbyWorms.range);assertEquals(1,restored.nearbyBarrels.range);
        assertEquals(ClickerConfig.ActionMode.HOLD,restored.clicker.actionMode);assertTrue(restored.mainLayout.widgets.contains(WidgetCatalog.LEVEL_GOAL.key()));
        var shared=SharedConfigCodec.pack(gson.toJsonTree(restored),new com.google.gson.JsonObject());
        assertEquals("HOLD",shared.getAsJsonObject("settings").getAsJsonObject("clicker").get("actionMode").getAsString());
        assertEquals(600,shared.getAsJsonObject("settings").getAsJsonObject("levelGoal").get("targetLevel").getAsInt());
    }
}
