package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public final class DwPotionTimersDecoder {
    private DwPotionTimersDecoder() {
    }

    public static DwPotionTimersPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int count = DwProtocolCodec.readCollectionSize(buf);
            List<DwPotionTimerEntry> entries = new ArrayList<>(Math.max(4, count));

            for (int i = 0; i < count; i++) {
                int id = DwProtocolCodec.readInt(buf);
                int quality = DwProtocolCodec.readInt(buf);
                long remainedMillis = DwProtocolCodec.readLong(buf);
                entries.add(new DwPotionTimerEntry(id, remainedMillis, quality));
            }

            return new DwPotionTimersPacket(entries);
        } finally {
            buf.release();
        }
    }
}
