package ru.wilyfox.client.highlight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UsefulWorldHighlightRenderHookTest {
    @Test
    void selectsNearestBarrelForTracer() {
        BlockPos nearest = UsefulWorldHighlightRenderHook.findNearestBarrel(
                List.of(new BlockPos(12, 64, 0), new BlockPos(3, 64, 0), new BlockPos(-8, 64, 0)),
                new Vec3(0.5D, 64.5D, 0.5D)
        );

        assertEquals(new BlockPos(3, 64, 0), nearest);
    }

    @Test
    void returnsNoTracerTargetWithoutBarrels() {
        assertNull(UsefulWorldHighlightRenderHook.findNearestBarrel(List.of(), Vec3.ZERO));
    }
}
