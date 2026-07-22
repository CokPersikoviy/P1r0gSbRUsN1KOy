package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DwHourlyQuestTypesDecoder {
    private DwHourlyQuestTypesDecoder() {
    }

    public static DwHourlyQuestTypesPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            int count = DwProtocolCodec.readVarInt(buf);
            Map<Integer, DwHourlyQuestType> types = new LinkedHashMap<>(Math.max(4, count));
            for (int i = 0; i < count; i++) {
                int id = DwProtocolCodec.readInt(buf);
                String type = DwProtocolCodec.readString(buf);
                String name = DwProtocolCodec.readString(buf);
                String lore = DwProtocolCodec.readString(buf);
                int needed = DwProtocolCodec.readInt(buf);
                types.put(id, new DwHourlyQuestType(id, type, name, lore, needed));
            }
            return new DwHourlyQuestTypesPacket(types);
        } finally {
            buf.release();
        }
    }
}
