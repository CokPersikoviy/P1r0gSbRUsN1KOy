package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DwBossTypesDecoder {
    private DwBossTypesDecoder() {
    }

    public static DwBossTypesPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int count = DwProtocolCodec.readVarInt(buf);
            Map<String, DwBossType> types = new LinkedHashMap<>(Math.max(4, count));

            for (int i = 0; i < count; i++) {
                String id = DwProtocolCodec.readString(buf);
                String name = DwProtocolCodec.readString(buf);
                String material = DwProtocolCodec.readString(buf);
                int level = DwProtocolCodec.readVarInt(buf);
                int customModelData = DwProtocolCodec.readVarInt(buf);
                int capturePoints = DwProtocolCodec.readVarInt(buf);
                boolean raid = DwProtocolCodec.readBoolean(buf);

                types.put(id, new DwBossType(id, name, material, level, customModelData, capturePoints, raid));
            }

            return new DwBossTypesPacket(types);
        } finally {
            buf.release();
        }
    }
}
