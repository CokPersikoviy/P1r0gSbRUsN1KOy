package ru.wilyfox.client.protocol;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DwClanStateTest {
    @Test
    void appliesOnlyFieldsPresentInPartialUpdate() {
        DwClanState initial = new DwClanState("Frogs", List.of("Fox", "Toad"), List.of("boss_a"));

        DwClanState updated = initial.applyPartial(Map.of("bosses", "[\"boss_b\",\"boss_c\"]"));

        assertEquals("Frogs", updated.name());
        assertEquals(List.of("Fox", "Toad"), updated.members());
        assertEquals(List.of("boss_b", "boss_c"), updated.bossIds());
    }

    @Test
    void malformedFieldKeepsLastGoodValueWithoutBlockingOtherFields() {
        DwClanState initial = new DwClanState("Frogs", List.of("Fox"), List.of("boss_a"));
        Map<String, String> update = new LinkedHashMap<>();
        update.put("members", "[12]");
        update.put("name", "\"New Frogs\"");

        DwClanState updated = initial.applyPartial(update);

        assertEquals("New Frogs", updated.name());
        assertEquals(List.of("Fox"), updated.members());
        assertEquals(List.of("boss_a"), updated.bossIds());
    }

    @Test
    void memberLookupIsCaseInsensitive() {
        DwClanState state = new DwClanState("Frogs", List.of("Some_Player"), List.of());

        assertTrue(state.containsMember("some_player"));
        assertFalse(state.containsMember("other_player"));
    }
}
