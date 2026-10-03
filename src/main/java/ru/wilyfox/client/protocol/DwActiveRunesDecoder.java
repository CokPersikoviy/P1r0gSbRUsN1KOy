package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public final class DwActiveRunesDecoder {
    private DwActiveRunesDecoder() {
    }

    public static DwActiveRunesPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int count = DwProtocolCodec.readCollectionSize(buf);
            List<String> runes = new ArrayList<>(Math.max(0, count));

            for (int i = 0; i < count; i++) {
                runes.add(DwProtocolCodec.readString(buf));
            }

            return new DwActiveRunesPacket(runes);
        } finally {
            buf.release();
        }
    }
}
