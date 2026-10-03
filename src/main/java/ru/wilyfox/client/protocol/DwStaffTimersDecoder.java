package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DwStaffTimersDecoder {
    private DwStaffTimersDecoder() {
    }

    public static DwStaffTimersPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int count = DwProtocolCodec.readCollectionSize(buf);
            Map<Integer, Long> timers = new LinkedHashMap<>();

            for (int i = 0; i < count; i++) {
                int id = DwProtocolCodec.readInt(buf);
                long remainingMillis = DwProtocolCodec.readLong(buf);
                timers.put(id, remainingMillis);
            }

            return new DwStaffTimersPacket(timers);
        } finally {
            buf.release();
        }
    }
}
