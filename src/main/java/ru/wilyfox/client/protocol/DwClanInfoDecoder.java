package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DwClanInfoDecoder {
    private DwClanInfoDecoder() {
    }

    public static DwClanInfoPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int size = DwProtocolCodec.readCollectionSize(buf);
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                values.put(DwProtocolCodec.readString(buf), DwProtocolCodec.readString(buf));
            }

            return new DwClanInfoPacket(values);
        } finally {
            buf.release();
        }
    }
}
