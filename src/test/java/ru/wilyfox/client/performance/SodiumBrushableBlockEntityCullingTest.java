package ru.wilyfox.client.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SodiumBrushableBlockEntityCullingTest {
    @Test
    void onlyRendersPositiveDustedLevels() {
        assertFalse(SodiumBrushableBlockEntityCulling.isActiveDustedLevel(-1));
        assertFalse(SodiumBrushableBlockEntityCulling.isActiveDustedLevel(0));
        assertTrue(SodiumBrushableBlockEntityCulling.isActiveDustedLevel(1));
        assertTrue(SodiumBrushableBlockEntityCulling.isActiveDustedLevel(3));
    }
}
