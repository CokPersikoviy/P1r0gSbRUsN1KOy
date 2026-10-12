package ru.wilyfox.client.highlight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.wilyfox.MinecraftTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.wilyfox.client.highlight.UsefulWorldHighlightRenderHook.HighlightBlockType.*;

class HeadHighlightBoundsTest {
    private static final BlockPos POSITION = new BlockPos(-17, 64, 31);

    @BeforeAll static void initializeMinecraft() { MinecraftTestBootstrap.initialize(); }

    @Test void allLuckyBlocksAndShardsFollowFloorSkullBounds() {
        var expected = new AABB(-16.77, 63.98, 31.23, -16.23, 64.52, 31.77);
        for (var type : new UsefulWorldHighlightRenderHook.HighlightBlockType[]{
                BASE_LUCKY_BLOCK, RARE_LUCKY_BLOCK, LEGENDARY_LUCKY_BLOCK, GOLDEN_SHARD, DIAMOND_SHARD}) {
            for (int rotation = 0; rotation < 16; rotation++) {
                var state = Blocks.PLAYER_HEAD.defaultBlockState().setValue(SkullBlock.ROTATION, rotation);
                assertEquals(expected, type.createBox(EmptyBlockGetter.INSTANCE, POSITION, state));
            }
        }
    }

    @Test void wallSkullsFollowTheirFacingInsteadOfTheFloorSkullBox() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var state = Blocks.PLAYER_WALL_HEAD.defaultBlockState().setValue(WallSkullBlock.FACING, facing);
            AABB local = switch (facing) {
                case NORTH -> new AABB(0.25, 0.25, 0.5, 0.75, 0.75, 1);
                case SOUTH -> new AABB(0.25, 0.25, 0, 0.75, 0.75, 0.5);
                case WEST -> new AABB(0.5, 0.25, 0.25, 1, 0.75, 0.75);
                case EAST -> new AABB(0, 0.25, 0.25, 0.5, 0.75, 0.75);
                default -> throw new AssertionError(facing);
            };
            assertEquals(local.move(POSITION).inflate(0.02),
                    BASE_LUCKY_BLOCK.createBox(EmptyBlockGetter.INSTANCE, POSITION, state));
        }
    }

    @Test void barrelsStillOutlineTheFullBlock() {
        var state = Blocks.NOTE_BLOCK.defaultBlockState();
        assertEquals(new AABB(-17, 64, 31, -16, 65, 32).inflate(0.02),
                NORMAL_BARREL.createBox(EmptyBlockGetter.INSTANCE, POSITION, state));
    }
}
