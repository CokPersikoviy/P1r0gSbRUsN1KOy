package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DwStaffTypesDecoder {
    private DwStaffTypesDecoder() {
    }

    public static DwStaffTypesPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int count = DwProtocolCodec.readCollectionSize(buf);
            Map<Integer, DwStaffType> types = new LinkedHashMap<>();

            for (int index = 0; index < count; index++) {
                String name = DwProtocolCodec.readString(buf);
                int modelId = DwProtocolCodec.readVarInt(buf);
                types.put(modelId, new DwStaffType(modelId, name, modelId));
            }

            return new DwStaffTypesPacket(types);
        } finally {
            buf.release();
        }
    }
}
