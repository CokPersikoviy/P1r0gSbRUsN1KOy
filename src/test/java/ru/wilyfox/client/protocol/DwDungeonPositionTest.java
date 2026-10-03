package ru.wilyfox.client.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import ru.wilyfox.MinecraftTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DwDungeonPositionTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void dungeonPositionUsesFixedMaskedIntsRatherThanVarints() {
        byte[] data = position(127, -128);
        assertEquals(8, data.length);
        assertEquals(new DwDungeonPosition(127, -128), DwDungeonPositionDecoder.decode(data));
        assertThrows(IndexOutOfBoundsException.class, () -> DwDungeonPositionDecoder.decode(new byte[7]));
    }

    @Test
    void routerStoresDungeonPositionAndDisconnectClearsIt() {
        ByteBuf envelope = Unpooled.buffer();
        try {
            DwProtocolCodec.writeString(envelope, "dungeonpos");
            envelope.writeBytes(position(254, 13));
            byte[] data = new byte[envelope.readableBytes()];
            envelope.readBytes(data);
            ProtocolState state = new ProtocolState();
            new ProtocolRouter().route(state, data);

            assertEquals(new DwDungeonPosition(254, 13), state.dungeonPosition);
            state.resetRuntimeState();
            assertNull(state.dungeonPosition);
        } finally {
            envelope.release();
        }
    }

    @Test
    void rejectedPositionDoesNotReplaceThePreviousPosition() {
        ProtocolState state = new ProtocolState();
        assertTrue(ProtocolPayloadHandlers.handleDungeonPosition(state, position(23, 45)));
        ProtocolPayloadHandlers.handleDungeonPosition(state, new byte[0]);
        assertEquals(new DwDungeonPosition(23, 45), state.dungeonPosition);
    }

    private static byte[] position(int x, int y) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(x ^ 0x67676767);
            buffer.writeInt(y ^ 0x67676767);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            buffer.release();
        }
    }
}
