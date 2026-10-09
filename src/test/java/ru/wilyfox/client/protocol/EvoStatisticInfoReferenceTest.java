package ru.wilyfox.client.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "froghelper.evoReference", matches = ".+")
class EvoStatisticInfoReferenceTest {
    @BeforeAll static void bootstrap() { ru.wilyfox.MinecraftTestBootstrap.initialize(); }
    private byte[] originalBytes(Map<String, String> fields) throws Exception {
        try (var loader = new URLClassLoader(new java.net.URL[]{Path.of(System.getProperty("froghelper.evoReference")).toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("sLADK1Yp3Rs1KoV1YS0k$50064812.SLaDKIyP3rSiKov1yS0K$862282309");
            Object packet = type.getConstructor(Map.class).newInstance(fields);
            ByteBuf buffer = Unpooled.buffer();
            try {
                type.getMethod("sLadkIyp3Rs1kOV1yS0K$904734075", ByteBuf.class).invoke(packet, buffer);
                byte[] data = new byte[buffer.readableBytes()]; buffer.readBytes(data); return data;
            } finally { buffer.release(); }
        }
    }
    @Test void locationPacketWrittenByReferenceUpdatesOurState() throws Exception {
        for (String id : List.of("bay", "shaft_125", "alchemy_nether", "boss_RatKing")) {
            var state = new ProtocolState();
            assertTrue(ProtocolPayloadHandlers.handleStatisticInfo(state, originalBytes(Map.of("gameLocation", "\"" + id + "\""))));
            assertEquals(id, state.currentGameLocation.id());
        }
    }
    @Test void largeAdjacentReferenceJsonDoesNotDiscardLocation() throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("statistic", "{\"large\":\"" + "x".repeat(40_000) + "\"}");
        fields.put("gameLocation", "\"shaft_125\"");
        byte[] encoded = originalBytes(fields);
        assertEquals(fields, DwStatisticInfoDecoder.decode(encoded).values());
        var state = new ProtocolState();
        assertTrue(ProtocolPayloadHandlers.handleStatisticInfo(state, encoded));
        assertEquals("shaft_125", state.currentGameLocation.id());
    }
    @Test void truncatedReferencePacketStillFailsBeforeAllocation() throws Exception {
        byte[] encoded = originalBytes(Map.of("gameLocation", "\"shaft_125\""));
        assertThrows(IllegalArgumentException.class, () -> DwStatisticInfoDecoder.decode(Arrays.copyOf(encoded, encoded.length - 1)));
    }
}
