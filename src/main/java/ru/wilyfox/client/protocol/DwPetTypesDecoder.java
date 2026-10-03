package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DwPetTypesDecoder {
    private DwPetTypesDecoder() {
    }

    public static DwPetTypesPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int count = DwProtocolCodec.readCollectionSize(buf);
            Map<String, DwPetType> types = new LinkedHashMap<>();

            for (int i = 0; i < count; i++) {
                String id = DwProtocolCodec.readString(buf);
                String name = DwProtocolCodec.readString(buf);
                String rarity = DwProtocolCodec.readString(buf);
                String material = DwProtocolCodec.readString(buf);
                int customModelData = DwProtocolCodec.readVarInt(buf);

                types.put(id, new DwPetType(id, name, rarity, material, customModelData));
            }

            return new DwPetTypesPacket(types);
        } finally {
            buf.release();
        }
    }
}
