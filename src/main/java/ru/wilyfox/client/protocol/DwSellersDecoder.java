package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public final class DwSellersDecoder {
    private DwSellersDecoder() {
    }

    public static DwSellersPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int count = DwProtocolCodec.readCollectionSize(buf);
            List<DwSellerEntry> entries = new ArrayList<>(Math.max(4, count));

            for (int i = 0; i < count; i++) {
                String id = DwProtocolCodec.readString(buf);
                String name = DwProtocolCodec.readString(buf);
                long remainingMillis = DwProtocolCodec.readLong(buf);
                entries.add(new DwSellerEntry(id, name, remainingMillis));
            }

            return new DwSellersPacket(entries);
        } finally {
            buf.release();
        }
    }
}
