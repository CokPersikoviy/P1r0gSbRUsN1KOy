package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DwAbilityTimersDecoder {
    private DwAbilityTimersDecoder() {
    }

    public static DwAbilityTimersPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int count = DwProtocolCodec.readCollectionSize(buf);
            Map<String, Long> timers = new LinkedHashMap<>();

            for (int i = 0; i < count; i++) {
                String id = DwProtocolCodec.readString(buf);
                long remainingMillis = DwProtocolCodec.readLong(buf);
                timers.put(id, remainingMillis);
            }

            return new DwAbilityTimersPacket(timers);
        } finally {
            buf.release();
        }
    }
}
