package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DwStatisticInfoDecoder {
    private DwStatisticInfoDecoder() {
    }

    public static DwStatisticInfoPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int count = DwProtocolCodec.readCollectionSize(buf);
            Map<String, String> values = new LinkedHashMap<>();

            for (int i = 0; i < count; i++) {
                String key = DwProtocolCodec.readString(buf);
                String value = DwProtocolCodec.readString(buf);
                values.put(key, value);
            }

            return new DwStatisticInfoPacket(values);
        } finally {
            buf.release();
        }
    }
}
