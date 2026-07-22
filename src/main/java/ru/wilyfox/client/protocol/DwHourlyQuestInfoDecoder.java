package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DwHourlyQuestInfoDecoder {
    private DwHourlyQuestInfoDecoder() {
    }

    public static DwHourlyQuestInfoPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            int count = DwProtocolCodec.readVarInt(buf);
            Map<Integer, DwHourlyQuestInfoPacket.Entry> quests = new LinkedHashMap<>(Math.max(4, count));
            for (int i = 0; i < count; i++) {
                int id = DwProtocolCodec.readInt(buf);
                int progress = DwProtocolCodec.readInt(buf);
                long remained = DwProtocolCodec.readLong(buf);
                quests.put(id, new DwHourlyQuestInfoPacket.Entry(id, progress, remained));
            }
            return new DwHourlyQuestInfoPacket(quests);
        } finally {
            buf.release();
        }
    }
}
