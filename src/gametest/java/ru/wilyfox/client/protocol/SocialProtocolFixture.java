package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;

/** Test-only injection at the DW packet boundary, without production test hooks. */
public final class SocialProtocolFixture {
    private SocialProtocolFixture() {}

    private static ProtocolState state() {
        try {
            Field field = DiamondWorldProtocolClient.class.getDeclaredField("STATE");
            field.setAccessible(true);
            return (ProtocolState) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    public static void token(String token) {
        var packet = Unpooled.buffer();
        try {
            DwProtocolCodec.writeString(packet, "token");
            packet.writeByte((token == null ? 0 : 1) ^ 103);
            if (token != null) DwProtocolCodec.writeString(packet, token);
            byte[] bytes = new byte[packet.readableBytes()];
            packet.readBytes(bytes);
            new ProtocolRouter().route(state(), bytes);
        } finally {
            packet.release();
        }
    }

    public static void location(String server, int mirror, String location) {
        ProtocolPayloadHandlers.applyServerInfo(state(), CurrentServerInfo.fromProtocol(server, mirror));
        state().currentGameLocation = new DwGameLocation(location);
    }

    public static void clear() {
        state().gameToken = null;
        state().currentServerInfo = CurrentServerInfo.unknown();
        state().currentGameLocation = null;
    }
}
