package ru.wilyfox.client.protocol;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.ability.AbilityCooldownStore;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolRegistryUpdateTest {
    @Test
    void abilityRegistryUpdatesPreserveOtherTypesAndTheirCooldownNames() {
        ProtocolState state = new ProtocolState();
        state.abilityCooldownStore = new AbilityCooldownStore();
        ProtocolPayloadHandlers.applyAbilityTypes(state,
                new DwAbilityTypesPacket(Map.of("a", new DwAbilityType("a", "First"))));
        state.abilityCooldownStore.replaceCooldowns(Map.of("a", 60_000L));
        ProtocolPayloadHandlers.applyAbilityTypes(state,
                new DwAbilityTypesPacket(Map.of("b", new DwAbilityType("b", "Second"))));
        ProtocolPayloadHandlers.applyAbilityTypes(state, new DwAbilityTypesPacket(Map.of()));

        assertEquals(Set.of("a", "b"), state.abilityTypes.keySet());
        assertEquals("First", state.abilityCooldownStore.getActiveEntries().getFirst().name());
        ProtocolPayloadHandlers.applyAbilityTypes(state,
                new DwAbilityTypesPacket(Map.of("a", new DwAbilityType("a", "Updated"))));
        assertEquals("Updated", state.abilityCooldownStore.getActiveEntries().getFirst().name());
    }

    @Test
    void petRegistryUpdatesPreserveOtherTypesAndReplaceOnlyMatchingIds() {
        ProtocolState state = new ProtocolState();
        DwPetType first = new DwPetType("a", "First", "rare", "STONE", 1);
        DwPetType second = new DwPetType("b", "Second", "rare", "STONE", 2);
        DwPetType updated = new DwPetType("a", "Updated", "epic", "STONE", 3);
        ProtocolPayloadHandlers.applyPetTypes(state, new DwPetTypesPacket(Map.of("a", first)));
        ProtocolPayloadHandlers.applyPetTypes(state, new DwPetTypesPacket(Map.of("b", second)));
        ProtocolPayloadHandlers.applyPetTypes(state, new DwPetTypesPacket(Map.of("a", updated)));
        ProtocolPayloadHandlers.applyPetTypes(state, new DwPetTypesPacket(Map.of()));

        assertEquals(Map.of("a", updated, "b", second), state.petTypes);
    }

    @Test
    void staffRegistryUpdatesPreserveOtherTypesAndResetAtDisconnect() {
        ProtocolState state = new ProtocolState();
        DwStaffType first = new DwStaffType(1, "First", 1);
        DwStaffType second = new DwStaffType(2, "Second", 2);
        ProtocolPayloadHandlers.applyStaffTypes(state, new DwStaffTypesPacket(Map.of(1, first)));
        ProtocolPayloadHandlers.applyStaffTypes(state, new DwStaffTypesPacket(Map.of(2, second)));
        ProtocolPayloadHandlers.applyStaffTypes(state, new DwStaffTypesPacket(Map.of()));

        assertEquals(Map.of(1, first, 2, second), state.staffTypes);
        state.resetRuntimeState();
        assertEquals(Map.of(), state.staffTypes);
    }
}
