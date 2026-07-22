package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public final class DwClanSiegePositionDecoder {
    private DwClanSiegePositionDecoder() {
    }

    public static DwClanSiegePosition decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            return new DwClanSiegePosition(DwProtocolCodec.readInt(buf), DwProtocolCodec.readInt(buf));
        } finally {
            buf.release();
        }
    }
}
