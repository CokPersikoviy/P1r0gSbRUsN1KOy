package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LocationUpdateOrderTest {
    @BeforeAll static void bootstrap() { ru.wilyfox.MinecraftTestBootstrap.initialize(); }
    @Test void brokenPetDataCannotBlockValidLocationInSamePacket() {
        var state = new ProtocolState(); state.currentGameLocation = new DwGameLocation("market");
        assertFalse(ProtocolPayloadHandlers.handleStatisticInfo(state, packet(Map.of("gameLocation", "\"bay\"", "pets", "broken"))));
        assertEquals("bay", state.currentGameLocation.id()); assertEquals(1, state.worldContextRevision);
    }
    @Test void brokenMinerDataCannotBlockValidLocationInSamePacket() {
        var state = new ProtocolState(); state.currentGameLocation = new DwGameLocation("bay");
        assertFalse(ProtocolPayloadHandlers.handleStatisticInfo(state, packet(Map.of("gameLocation", "\"shaft_125\"", "miners", "42"))));
        assertEquals("shaft_125", state.currentGameLocation.id());
    }
    @Test void repeatedLocationIsFreshDataButNotAnotherContextTransition() {
        var state = new ProtocolState(); var p = new DwStatisticInfoPacket(Map.of("gameLocation", "\"bay\""));
        ProtocolPayloadHandlers.updateGameLocation(state, p);
        long context = state.worldContextRevision, freshness = state.gameLocationRevision;
        ProtocolPayloadHandlers.updateGameLocation(state, p);
        assertEquals(context, state.worldContextRevision); assertEquals(freshness + 1, state.gameLocationRevision);
        ProtocolPayloadHandlers.updateGameLocation(state, new DwStatisticInfoPacket(Map.of("gameLocation", "\"market\"")));
        assertEquals(context + 1, state.worldContextRevision);
    }
    private static byte[] packet(Map<String, String> data) {
        var buffer = Unpooled.buffer();
        try {
            DwProtocolCodec.writeVarInt(buffer, data.size());
            data.forEach((key, value) -> { DwProtocolCodec.writeString(buffer, key); DwProtocolCodec.writeString(buffer, value); });
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes); return bytes;
        } finally { buffer.release(); }
    }
}
