package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public final class DwPotionTypesDecoder {
    private DwPotionTypesDecoder() {
    }

    public static DwPotionTypesPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int count = DwProtocolCodec.readCollectionSize(buf);
            List<DwPotionTypeEntry> entries = new ArrayList<>(Math.max(4, count));

            for (int i = 0; i < count; i++) {
                String name = DwProtocolCodec.readString(buf);
                int id = DwProtocolCodec.readVarInt(buf);
                entries.add(new DwPotionTypeEntry(id, id, name));
            }

            return new DwPotionTypesPacket(entries);
        } finally {
            buf.release();
        }
    }
}
