package ru.wilyfox.client.protocol;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FishingProtocolUpdateTest {
    @Test
    void emptyNamesUseLocationIdsAndInvalidIdsDoNotRejectTheWholePacket() {
        ProtocolState state = new ProtocolState();
        state.fishingLocationIds.add("existing");
        state.fishingLocationNames.put("existing", "Existing location");
        Map<String, String> locations = new LinkedHashMap<>();
        locations.put(" BAY ", "\"Рыбацкая бухта\"");
        locations.put(" SWAMP ", "");
        locations.put("  ", "Invalid location");
        locations.put("crystal", null);

        ProtocolPayloadHandlers.applyFishingSpots(state, new DwFishingSpotsPacket(locations));

        assertEquals(Set.of("existing", "bay", "swamp", "crystal"), state.fishingLocationIds);
        assertEquals(Map.of("existing", "Existing location", "bay", "Рыбацкая бухта",
                "swamp", "swamp", "crystal", "crystal"), state.fishingLocationNames);
    }

    @Test
    void emptyNibbleIdsAreIgnoredAndUpdatesPreserveEarlierLocations() {
        ProtocolState state = new ProtocolState();
        state.fishingNibbles.put("existing", 0.75D);
        Map<String, Double> nibbles = new LinkedHashMap<>();
        nibbles.put(" BAY ", 0.5D);
        nibbles.put(" ", 1.0D);
        nibbles.put("crystal", null);

        ProtocolPayloadHandlers.applySpotNibbles(state, new DwSpotNibblesPacket(nibbles));

        assertEquals(Map.of("existing", 0.75D, "bay", 0.5D, "crystal", 0.0D), state.fishingNibbles);
    }
}
