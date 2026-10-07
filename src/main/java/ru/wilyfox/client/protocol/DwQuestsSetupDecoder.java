package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DwQuestsSetupDecoder {
    private DwQuestsSetupDecoder() {}

    public static Map<String, DwQuest> decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            int count = DwProtocolCodec.readCollectionSize(buf);
            Map<String, DwQuest> quests = new LinkedHashMap<>(Math.max(4, count));
            for (int i = 0; i < count; i++) {
                String id = DwProtocolCodec.readString(buf);
                DwQuest.Dimension dimension = DwQuest.Dimension.values()[DwProtocolCodec.readVarInt(buf)];
                DwQuest.Category category = DwQuest.Category.values()[DwProtocolCodec.readVarInt(buf)];
                String name = DwProtocolCodec.readString(buf);
                String description = DwProtocolCodec.readString(buf);
                int progress = DwProtocolCodec.readVarInt(buf);
                int required = DwProtocolCodec.readVarInt(buf);
                quests.put(id, new DwQuest(id, dimension, category, name, description, progress, required));
            }
            if (buf.isReadable()) throw new IllegalArgumentException("Trailing questssetup data");
            return quests;
        } finally {
            buf.release();
        }
    }
}
