package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DwAbilityTypesDecoder {
    private DwAbilityTypesDecoder() {
    }

    public static DwAbilityTypesPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int count = DwProtocolCodec.readCollectionSize(buf);
            Map<String, DwAbilityType> types = new LinkedHashMap<>();

            for (int i = 0; i < count; i++) {
                String id = DwProtocolCodec.readString(buf);
                String name = DwProtocolCodec.readString(buf);
                types.put(id, new DwAbilityType(id, name));
            }

            return new DwAbilityTypesPacket(types);
        } finally {
            buf.release();
        }
    }
}
