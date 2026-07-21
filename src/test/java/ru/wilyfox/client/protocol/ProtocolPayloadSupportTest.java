package ru.wilyfox.client.protocol;

import net.minecraft.core.component.DataComponents;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.component.CustomModelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.miner.ActiveMinerInfo;
import ru.wilyfox.client.pet.ActivePetInfo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        );

        assertEquals(2, result.getFirst().level());
        assertEquals(Instant.parse(isoTime).toEpochMilli(), result.getFirst().homecomingAt());
        assertEquals(1, result.get(1).level());
        assertEquals(1_234_567_890_000L, result.get(1).homecomingAt());
    }

    @Test
    void expiredTravelIsEffectivelyComplete() {
        ActiveMinerInfo miner = new ActiveMinerInfo(null, 1, "Mobs", "IN_TRAVEL", 1_000L);

        assertTrue(miner.isComplete(1_001L));
        assertFalse(miner.isComplete(999L));
    }

    @Test
    void petsUseRegistryNameMaterialAndCustomModelData() {
        ProtocolState state = new ProtocolState();
        state.petTypes = Map.of("WOLF", new DwPetType("WOLF", "Wolf", "COMMON", "minecraft:bone", 321));
        String pets = "[{\"pet\":\"WOLF\",\"level\":5,\"exp\":12.5,\"energy\":7.4}]";

        ActivePetInfo pet = ProtocolPayloadSupport.extractActivePets(
                state,
                new DwStatisticInfoPacket(Map.of("pets", pets))
        ).getFirst();

        assertEquals("Wolf", pet.name());
        CustomModelData modelData = pet.icon().get(DataComponents.CUSTOM_MODEL_DATA);
        assertEquals(321.0F, modelData.getFloat(0));
    }
}
