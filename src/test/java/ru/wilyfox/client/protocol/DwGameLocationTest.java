package ru.wilyfox.client.protocol;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DwGameLocationTest {
    @Test
    void classifiesDungeonFamiliesSeparately() {
        DwGameLocation regular = new DwGameLocation("dungeon_forest");
        DwGameLocation procedural = new DwGameLocation("procedural_dungeon_camp");

        assertTrue(regular.isDungeon());
        assertFalse(regular.isProceduralDungeon());
        assertEquals("forest", regular.dungeonId());

        assertFalse(procedural.isDungeon());
        assertTrue(procedural.isProceduralDungeon());
        assertTrue(procedural.isAnyDungeon());
        assertEquals("camp", procedural.dungeonId());
    }

    @Test
    void classifiesFishingSpotsAndNotFishingWarps() {
        for (String id : new String[]{
                "bay", "azurepond", "citycanal", "swamp", "ambergrot", "basalt",
                "netherval", "magma", "endwharf", "silence", "crystal"
        }) {
            assertTrue(new DwGameLocation(id).isFishing(), id);
        }

        assertFalse(new DwGameLocation("fish_1_overworld").isFishing());
        assertFalse(new DwGameLocation("fish_nether").isFishing());
    }

    @Test
    void exposesExactLocationPresentationAndShaftKinds() {
        assertEquals("\u0410\u043b\u0445\u0438\u043c\u0438\u044f", new DwGameLocation("alchemy").displayName());
        assertEquals("\u042f\u043d\u0442\u0430\u0440\u043d\u044b\u0439 \u0433\u0440\u043e\u0442", new DwGameLocation("ambergrot").displayName());
        assertEquals("\u0414\u0430\u043d\u0436: \u041b\u0430\u0433\u0435\u0440\u044c \u0440\u0430\u0437\u0431\u043e\u0439\u043d\u0438\u043a\u043e\u0432", new DwGameLocation("procedural_dungeon_camp").displayName());
        assertEquals("\u0428\u0430\u0445\u0442\u0430 17 \u0443\u0440.", new DwGameLocation("shaft_17").displayName());
        assertTrue(new DwGameLocation("shaft_elite").isEliteShaft());
        assertTrue(new DwGameLocation("shaft_clan").isClanShaft());
    }

    @Test
    void statisticLocationRequiresGameLocationAndKeepsLastGoodValue() {
        ProtocolState state = new ProtocolState();
        state.currentGameLocation = new DwGameLocation("alchemy");

        ProtocolPayloadHandlers.updateGameLocation(
                state,
                new DwStatisticInfoPacket(Map.of("location", "\"bay\""))
        );
        assertEquals("alchemy", state.currentGameLocation.id());

        ProtocolPayloadHandlers.updateGameLocation(
                state,
                new DwStatisticInfoPacket(Map.of("gameLocation", "\"bay\""))
        );
        assertEquals("bay", state.currentGameLocation.id());

        ProtocolPayloadHandlers.updateGameLocation(
                state,
                new DwStatisticInfoPacket(Map.of("gameLocation", "{\"id\":\"swamp\"}"))
        );
        assertEquals("bay", state.currentGameLocation.id());
    }

    @Test
    void parsesOnlyStringLocationValues() {
        assertEquals("alchemy", ProtocolPayloadSupport.parseGameLocation("\"alchemy\"").id());
        assertEquals("spawn", ProtocolPayloadSupport.parseGameLocation("spawn").id());
        assertNull(ProtocolPayloadSupport.parseGameLocation("123"));
        assertNull(ProtocolPayloadSupport.parseGameLocation("{\"id\":\"spawn\"}"));
    }
}
