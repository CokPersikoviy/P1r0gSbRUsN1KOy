package ru.wilyfox.client.rune;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveRunesStoreTest {
    @Test
    void snapshotsSurviveInputMutationReplaceAndDisconnectClear() {
        ActiveRunesStore store = new ActiveRunesStore();
        List<String> input = new ArrayList<>(List.of("Strength", "Luck"));
        store.replace(input);
        List<String> displayed = store.getAll();
        input.clear();
        assertEquals(List.of("Strength", "Luck"), store.getAll());
        assertThrows(UnsupportedOperationException.class, () -> displayed.add("Speed"));

        store.replace(List.of("Speed"));
        store.clear();
        assertTrue(store.isEmpty());
        assertEquals(List.of(), store.getAll());
        assertEquals(List.of("Strength", "Luck"), displayed);
    }
}
