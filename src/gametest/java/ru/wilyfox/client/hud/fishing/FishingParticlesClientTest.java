package ru.wilyfox.client.hud.fishing;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ParticleStatus;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.protocol.SocialProtocolFixture;

public final class FishingParticlesClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        boolean[] originalMarkers = new boolean[1];
        ParticleStatus[] originalParticles = new ParticleStatus[1];
        var tracker = FishingSpotTracker.getInstance();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                originalMarkers[0] = ConfigManager.get().fishing.showFishingMarkers;
                originalParticles[0] = client.options.particles().get();
                ConfigManager.get().fishing.showFishingMarkers = true;
                client.options.particles().set(ParticleStatus.MINIMAL);
                SocialProtocolFixture.location("PRISONEVO1", 2, "bay");

                // Exercise the real listener injection, including parameterized DUST and count zero.
                for (ParticleOptions type : new ParticleOptions[]{ParticleTypes.BUBBLE, ParticleTypes.LAVA,
                        new DustParticleOptions(0x00FF00, 1F), new DustParticleOptions(0xFF0000, 2F),
                        ParticleTypes.BUBBLE_POP, ParticleTypes.BUBBLE_COLUMN_UP}) {
                    tracker.clear();
                    client.getConnection().handleParticleEvent(packet(type, 0));
                    var spots = tracker.getActiveSpots();
                    if (spots.size() != 1) fail("Missing marker for " + type.getType() + " at minimal particle settings");
                    var spot = spots.getFirst();
                    if (spot.center().x != 1D || spot.center().y != 65.25D || spot.center().z != 2D) {
                        fail("Packet center was replaced by a randomized rendered particle position");
                    }
                    if (tracker.getActiveBubbles().size() != 1) fail("Particle engine duplicated the packet signal");
                }
                tracker.clear();
                client.getConnection().handleParticleEvent(packet(ParticleTypes.SMOKE, 10));
                client.getConnection().handleParticleEvent(packet(ParticleTypes.DUST_PLUME, 10));
                if (!tracker.getActiveSpots().isEmpty()) fail("Unrelated particles created fishing markers");

                client.getConnection().handleParticleEvent(packet(new DustParticleOptions(0xFFFFFF, 1F), 20));
                if (tracker.getActiveBubbles().size() != 1 || tracker.getActiveSpots().getFirst().bubbleCount() != 20) {
                    fail("One particle packet was expanded into individual tracked particles");
                }
                SocialProtocolFixture.location("PRISONEVO1", 2, "crystal");
                if (!tracker.getActiveSpots().isEmpty()) fail("Previous fishing location kept stale markers");
                SocialProtocolFixture.location("PRISONEVO1", 2, "spawn_overworld");
                client.getConnection().handleParticleEvent(packet(DustParticleOptions.REDSTONE, 1));
                if (!tracker.getActiveSpots().isEmpty()) fail("Dust outside fishing locations produced markers");

                SocialProtocolFixture.location("PRISONEVO1", 2, "bay");
                ConfigManager.get().fishing.showFishingMarkers = false;
                client.getConnection().handleParticleEvent(packet(DustParticleOptions.REDSTONE, 1));
                if (tracker.diagnosticParticleCount() != 0) fail("Disabled markers still collected packets");
                ConfigManager.get().fishing.showFishingMarkers = true;
                for (int i = 0; i < 2_100; i++) tracker.onParticlePacket(packet(DustParticleOptions.REDSTONE, Integer.MAX_VALUE));
                if (tracker.diagnosticParticleCount() > 2_048) fail("Particle packet history is unbounded");
                tracker.clear();
                client.getConnection().handleParticleEvent(packet(DustParticleOptions.REDSTONE, 1));
            });
            context.waitTicks(26);
            context.runOnClient(client -> {
                if (!tracker.getActiveSpots().isEmpty()) fail("Fishing marker survived its one-second lifetime");
            });
        } finally {
            context.runOnClient(client -> {
                ConfigManager.get().fishing.showFishingMarkers = originalMarkers[0];
                if (originalParticles[0] != null) client.options.particles().set(originalParticles[0]);
                tracker.clear();
                SocialProtocolFixture.clear();
            });
        }
    }

    private static ClientboundLevelParticlesPacket packet(ParticleOptions type, int count) {
        return new ClientboundLevelParticlesPacket(type, false, false, 1D, 65D, 2D, 0.5F, 0.5F, 0.5F, 0F, count);
    }

    private static void fail(String message) { throw new AssertionError(message); }
}
