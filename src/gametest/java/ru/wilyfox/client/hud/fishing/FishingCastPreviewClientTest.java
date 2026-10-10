package ru.wilyfox.client.hud.fishing;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import ru.wilyfox.client.hud.config.ConfigManager;

public final class FishingCastPreviewClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        boolean original = ConfigManager.get().fishing.showCastPreview;
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(mc -> {
                mc.gui.setScreen(null);
                mc.player.setNoGravity(true);
                mc.player.snapTo(0, 102, 0, 0, 30);
                mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
                for (int x = -6; x <= 6; x++) for (int z = -4; z <= 14; z++) {
                    mc.level.setBlock(new BlockPos(x, 100, z), Blocks.WATER.defaultBlockState(), 0);
                }
                ConfigManager.get().fishing.showCastPreview = false;
                FishingCastPreview.tick(mc);
                expect(FishingCastPreview.predictedPosition() == null, "Disabled preview calculated a cast");
                ConfigManager.get().fishing.showCastPreview = true;
                FishingCastPreview.tick(mc);
                Vec3 water = FishingCastPreview.predictedPosition();
                double surface = 100 + mc.level.getFluidState(new BlockPos(0, 100, 4))
                        .getHeight(mc.level, new BlockPos(0, 100, 4));
                expect(water != null && Math.abs(water.y - surface) < 0.01, "Did not stop at the water surface: " + water);
                expect(mc.player.fishing == null, "Preview created a real fishing hook");

                mc.player.setYRot(90);
                FishingCastPreview.tick(mc);
                expect(FishingCastPreview.predictedPosition() == water, "Trajectory recomputed within 100 ms");
                mc.player.setYRot(0);

                // Compare mean launch against actual 26.2 hooks, including the vanilla random spread.
                var mean = FishingCastPrediction.launch(mc.player.getEyePosition(), 0, 30);
                Vec3 average = Vec3.ZERO;
                for (int i = 0; i < 128; i++) {
                    var hook = new FishingHook(mc.player, mc.level, 0, 0);
                    expect(hook.position().distanceTo(mean.position()) < 1.0E-6, "Launch origin differs from vanilla");
                    average = average.add(hook.getDeltaMovement());
                    hook.discard();
                }
                mc.player.fishing = null;
                expect(average.scale(1.0 / 128).distanceTo(mean.velocity()) < 0.005, "Launch velocity differs from vanilla");

                // A wall must win over water behind it, and volume collision must keep the hook outside it.
                for (int x = -2; x <= 2; x++) for (int y = 101; y <= 106; y++) {
                    mc.level.setBlock(new BlockPos(x, y, 2), Blocks.STONE.defaultBlockState(), 0);
                }
                FishingCastPreview.clear(); FishingCastPreview.tick(mc);
                Vec3 wall = FishingCastPreview.predictedPosition();
                expect(wall != null && wall.z <= 1.875 + 1.0E-6, "Ignored a blocking wall: " + wall);
                for (int x = -2; x <= 2; x++) for (int y = 101; y <= 106; y++) {
                    mc.level.setBlock(new BlockPos(x, y, 2), Blocks.AIR.defaultBlockState(), 0);
                }
                var near = new ArmorStand(mc.level, 0, 101.2, 2.3);
                var far = new ArmorStand(mc.level, 0, 101.2, 3.2);
                near.setId(2_950_001); far.setId(2_950_002);
                mc.level.addEntity(far); mc.level.addEntity(near); // The nearest must win regardless of query order.
                FishingCastPreview.clear(); FishingCastPreview.tick(mc);
                expect(FishingCastPreview.predictedEntity() == near, "Cast did not hook the nearest entity");
                Vec3 attachment = new Vec3(near.getX(), near.getY(0.8), near.getZ());
                expect(FishingCastPreview.predictedPosition().distanceTo(attachment) < 1.0E-6,
                        "Entity attachment point differs from vanilla");
                near.snapTo(0.1, 101.2, 2.3, 0, 0);
                expect(Math.abs(FishingCastPreview.predictedPosition().x - 0.1) < 1.0E-6,
                        "Marker did not follow a moving entity between predictions");
                near.discard();
                expect(FishingCastPreview.predictedPosition() == null, "Removed entity retained its marker");
                far.discard();

                // A solid wall must hide an entity behind it.
                var hidden = new ArmorStand(mc.level, 0, 101.2, 3.2);
                hidden.setId(2_950_003); mc.level.addEntity(hidden);
                for (int x = -2; x <= 2; x++) for (int y = 101; y <= 106; y++)
                    mc.level.setBlock(new BlockPos(x, y, 2), Blocks.STONE.defaultBlockState(), 0);
                FishingCastPreview.clear(); FishingCastPreview.tick(mc);
                expect(FishingCastPreview.predictedEntity() == null, "Cast hooked an entity through a wall");
                for (int x = -2; x <= 2; x++) for (int y = 101; y <= 106; y++)
                    mc.level.setBlock(new BlockPos(x, y, 2), Blocks.AIR.defaultBlockState(), 0);
                hidden.discard();

                // Water is reached before this distant entity.
                var beyondWater = new ArmorStand(mc.level, 0, 100, 8);
                beyondWater.setId(2_950_004); mc.level.addEntity(beyondWater);
                FishingCastPreview.clear(); FishingCastPreview.tick(mc);
                expect(FishingCastPreview.predictedEntity() == null
                        && Math.abs(FishingCastPreview.predictedPosition().y - surface) < 0.01,
                        "Entity behind the water intercepted the cast");
                beyondWater.discard();

                // FishingHook can catch dropped items even though they are not pickable projectiles' targets.
                var item = new ItemEntity(mc.level, 0, 101.8, 3.1, new ItemStack(Items.DIAMOND));
                item.setId(2_950_005); mc.level.addEntity(item);
                FishingCastPreview.clear(); FishingCastPreview.tick(mc);
                expect(FishingCastPreview.predictedEntity() == item, "Cast ignored a dropped item");
                item.discard();
                expect(mc.player.fishing == null, "Entity prediction created a real hook");
                mc.player.fishing = new FishingHook(mc.player, mc.level, 0, 0);
                expect(!FishingCastPreview.eligible(mc), "Preview remained visible after casting");
                FishingCastPreview.tick(mc);
                expect(FishingCastPreview.predictedPosition() == null, "Casting retained the cached preview");
                mc.player.fishing.discard(); mc.player.fishing = null;
                mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                expect(!FishingCastPreview.eligible(mc), "Preview visible without a rod");
                mc.player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.FISHING_ROD));
                expect(FishingCastPreview.eligible(mc), "Offhand rod cannot preview its cast");
                FishingCastPreview.clear(); FishingCastPreview.tick(mc);
            });
            context.waitTicks(3);
            context.takeScreenshot("fishing-cast-preview-water");
            context.runOnClient(mc -> {
                ConfigManager.get().fishing.showCastPreview = false;
                FishingCastPreview.tick(mc);
                expect(FishingCastPreview.predictedPosition() == null, "Disabling did not clear the preview");
            });
        } finally {
            context.runOnClient(mc -> {
                ConfigManager.get().fishing.showCastPreview = original;
                FishingCastPreview.clear();
            });
        }
    }
    private static void expect(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
