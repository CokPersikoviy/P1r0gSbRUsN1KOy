package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public final class DwDungeonPositionDecoder {
    private DwDungeonPositionDecoder() {
    }

    public static DwDungeonPosition decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            return new DwDungeonPosition(DwProtocolCodec.readInt(buf), DwProtocolCodec.readInt(buf));
        } finally {
            buf.release();
        }
    }
}
