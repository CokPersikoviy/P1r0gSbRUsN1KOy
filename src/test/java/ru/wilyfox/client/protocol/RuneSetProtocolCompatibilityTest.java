package ru.wilyfox.client.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import ru.wilyfox.MinecraftTestBootstrap;
import ru.wilyfox.client.rune.RuneSetCooldownStore;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuneSetProtocolCompatibilityTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @AfterEach
    void clearCooldown() {
        RuneSetCooldownStore.clear();
    }

    @Test
    void exactWholeSecondsSignalRuneSwapCooldownIncludingZero() {
        assertTrue(ProtocolPayloadSupport.shouldTriggerRuneSetCooldown(Map.of("WIND", 10_000L)));
        assertTrue(ProtocolPayloadSupport.shouldTriggerRuneSetCooldown(Map.of("WIND", 0L)));
        assertFalse(ProtocolPayloadSupport.shouldTriggerRuneSetCooldown(Map.of("WIND", 10_001L)));
        assertFalse(ProtocolPayloadSupport.shouldTriggerRuneSetCooldown(Map.of("WIND", 9_999L)));
        assertFalse(ProtocolPayloadSupport.shouldTriggerRuneSetCooldown(Map.of()));
    }

    @Test
    void exclusionsMatch332AndDoNotDependOnDisplayNames() {
        for (String id : new String[]{"SERAPHIM", "PHOENIX", "GOD", "POSEIDON"}) {
            assertFalse(ProtocolPayloadSupport.shouldTriggerRuneSetCooldown(Map.of(id, 10_000L)), id);
        }
        assertTrue(ProtocolPayloadSupport.shouldTriggerRuneSetCooldown(Map.of("ILLUSIONER", 10_000L)));
        assertTrue(ProtocolPayloadSupport.shouldTriggerRuneSetCooldown(Map.of("TURTLE", 10_000L)));
        assertTrue(ProtocolPayloadSupport.shouldTriggerRuneSetCooldown(Map.of("POSEIDON", 10_000L, "WIND", 4_000L)));
    }

    @Test
    void abilityHandlerSetsFullTenSecondsRatherThanReducingTheCooldown() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            DwProtocolCodec.writeVarInt(buffer, 1);
            DwProtocolCodec.writeString(buffer, "WIND");
            buffer.writeLong(10_000L ^ 0x6767676767676767L);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);

            assertTrue(ProtocolPayloadHandlers.handleAbilityTimers(new ProtocolState(), data));
            assertTrue(RuneSetCooldownStore.getRemainingMillis() > 9_500L);
            assertTrue(RuneSetCooldownStore.getRemainingMillis() <= 10_000L);
        } finally {
            buffer.release();
        }
    }
}
