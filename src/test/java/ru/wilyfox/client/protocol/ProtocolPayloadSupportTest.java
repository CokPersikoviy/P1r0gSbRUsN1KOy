package ru.wilyfox.client.protocol;

import net.minecraft.core.component.DataComponents;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.component.CustomModelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.miner.ActiveMinerInfo;
import ru.wilyfox.client.pet.ActivePetInfo;
import ru.wilyfox.client.pet.ActivePetsStore;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolPayloadSupportTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void minersUseEvoPlusLevelCurveAndAcceptBothTimeFormats() {
        String isoTime = "2030-01-02T03:04:05Z";
        String miners = """
                [
                  {"status":"IN_TRAVEL","exp":74,"data":{"category":"MOBS","homecoming":"%s"},"spriteIdOffset":2},
                  {"status":"IN_TRAVEL","exp":30,"data":{"category":"CASES","homecoming":1234567890000},"spriteIdOffset":3}
                ]
                """.formatted(isoTime);

        ProtocolState state = new ProtocolState();
        state.loggedFirstMinerPayload = true;
        List<ActiveMinerInfo> result = ProtocolPayloadSupport.extractActiveMiners(
                state,
                new DwStatisticInfoPacket(Map.of("miners", miners))
        ).orElseThrow();

        assertEquals(2, result.getFirst().level());
        assertEquals(Instant.parse(isoTime).toEpochMilli(), result.getFirst().homecomingAt());
        assertEquals(1, result.get(1).level());
        assertEquals(1_234_567_890_000L, result.get(1).homecomingAt());
    }

    @Test
    void expiredTravelIsEffectivelyComplete() {
        long receivedAt = 1_000_000L;
        ActiveMinerInfo miner = new ActiveMinerInfo(
                null,
                1,
                "MOBS",
                2,
                "Mobs",
                "IN_TRAVEL",
                1_000L,
                receivedAt,
                10_000L
        );

        assertFalse(miner.isCompleteAt(receivedAt + TimeUnit.SECONDS.toNanos(9L)));
        assertTrue(miner.isCompleteAt(receivedAt + TimeUnit.SECONDS.toNanos(10L)));
        assertEquals(6_000L, miner.remainingMillisAt(receivedAt + TimeUnit.SECONDS.toNanos(4L)));
    }

    @Test
    void singleMinerObjectIsNormalizedAndLevelComesOnlyFromExperience() {
        ProtocolState state = new ProtocolState();
        state.loggedFirstMinerPayload = true;
        String minerObject = """
                {"status":"IN_TRAVEL","exp":31,"level":99,
                 "data":{"category":"MOBS","homecoming":1234567890000},"spriteIdOffset":2}
                """;

        List<ActiveMinerInfo> miners = ProtocolPayloadSupport.extractActiveMiners(
                state,
                new DwStatisticInfoPacket(Map.of("miners", minerObject))
        ).orElseThrow();

        assertEquals(1, miners.size());
        assertEquals(2, miners.getFirst().level());
        assertEquals("MOBS_2", miners.getFirst().id());
    }

    @Test
    void missingOrNullMinersValueDoesNotReplacePreviousSnapshot() {
        ProtocolState state = new ProtocolState();

        assertTrue(ProtocolPayloadSupport.extractActiveMiners(
                state,
                new DwStatisticInfoPacket(Map.of("location", "spawn"))
        ).isEmpty());
        assertTrue(ProtocolPayloadSupport.extractActiveMiners(
                state,
                new DwStatisticInfoPacket(Map.of("miners", "null"))
        ).isEmpty());
    }

    @Test
    void emptyMinerArrayIsAnExplicitEmptySnapshot() {
        ProtocolState state = new ProtocolState();

        List<ActiveMinerInfo> miners = ProtocolPayloadSupport.extractActiveMiners(
                state,
                new DwStatisticInfoPacket(Map.of("miners", "[]"))
        ).orElseThrow();

        assertTrue(miners.isEmpty());
    }

    @Test
    void minersInSameCategoryRemainDistinctBySpriteOffset() {
        ProtocolState state = new ProtocolState();
        state.loggedFirstMinerPayload = true;
        String minersJson = """
                [
                  {"status":"IN_TRAVEL","exp":0,"data":{"category":"MOBS"},"spriteIdOffset":1},
                  {"status":"IN_TRAVEL","exp":0,"data":{"category":"MOBS"},"spriteIdOffset":2}
                ]
                """;

        List<ActiveMinerInfo> miners = ProtocolPayloadSupport.extractActiveMiners(
                state,
                new DwStatisticInfoPacket(Map.of("miners", minersJson))
        ).orElseThrow();

        assertEquals(2, miners.size());
        assertEquals(List.of("MOBS_1", "MOBS_2"), miners.stream().map(ActiveMinerInfo::id).toList());
    }

    @Test
    void petsUseRegistryNameMaterialAndCustomModelData() {
        ProtocolState state = new ProtocolState();
        state.petTypes = Map.of("WOLF", new DwPetType("WOLF", "Wolf", "COMMON", "minecraft:bone", 321));
        String pets = "[{\"pet\":\"WOLF\",\"level\":5,\"exp\":12.5,\"energy\":7.4}]";

        ActivePetInfo pet = ProtocolPayloadSupport.extractActivePets(
                state,
                new DwStatisticInfoPacket(Map.of("pets", pets))
        ).orElseThrow().getFirst();

        assertEquals("Wolf", pet.name());
        assertTrue(pet.resolved());
        CustomModelData modelData = pet.icon().get(DataComponents.CUSTOM_MODEL_DATA);
        assertEquals(321.0F, modelData.getFloat(0));
    }

    @Test
    void singlePetObjectIsNormalizedToList() {
        ProtocolState state = new ProtocolState();
        String petObject = "{\"pet\":\"WOLF\",\"level\":5,\"exp\":12.5,\"energy\":7.4}";

        List<ActivePetInfo> pets = ProtocolPayloadSupport.extractActivePets(
                state,
                new DwStatisticInfoPacket(Map.of("pets", petObject))
        ).orElseThrow();

        assertEquals(1, pets.size());
        assertEquals("WOLF", pets.getFirst().id());
        assertFalse(pets.getFirst().resolved());
    }

    @Test
    void missingOrNullPetsValueDoesNotReplacePreviousSnapshot() {
        ProtocolState state = new ProtocolState();

        assertTrue(ProtocolPayloadSupport.extractActivePets(
                state,
                new DwStatisticInfoPacket(Map.of("location", "spawn"))
        ).isEmpty());
        assertTrue(ProtocolPayloadSupport.extractActivePets(
                state,
                new DwStatisticInfoPacket(Map.of("pets", "null"))
        ).isEmpty());
    }

    @Test
    void emptyPetArrayIsAnExplicitEmptySnapshot() {
        ProtocolState state = new ProtocolState();

        List<ActivePetInfo> pets = ProtocolPayloadSupport.extractActivePets(
                state,
                new DwStatisticInfoPacket(Map.of("pets", "[]"))
        ).orElseThrow();

        assertTrue(pets.isEmpty());
    }

    @Test
    void unresolvedPetBecomesVisibleAfterPetTypesArrive() {
        ProtocolState state = new ProtocolState();
        String petsJson = "[{\"pet\":\"WOLF\",\"level\":5,\"exp\":12.5,\"energy\":7.4}]";
        ActivePetsStore store = new ActivePetsStore();
        store.replace(ProtocolPayloadSupport.extractActivePets(
                state,
                new DwStatisticInfoPacket(Map.of("pets", petsJson))
        ).orElseThrow());

        assertFalse(store.hasResolved());

        state.petTypes = Map.of("WOLF", new DwPetType("WOLF", "Wolf", "COMMON", "minecraft:bone", 321));
        store.replace(store.getAll().stream()
                .map(pet -> ProtocolPayloadSupport.enrichActivePet(state, pet))
                .toList());

        assertTrue(store.hasResolved());
        assertEquals("Wolf", store.getResolved().getFirst().name());
    }
}
