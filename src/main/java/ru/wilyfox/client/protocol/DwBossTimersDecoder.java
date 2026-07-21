package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DwBossTimersDecoder {
    private DwBossTimersDecoder() {
    }

    public static DwBossTimersPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int count = DwProtocolCodec.readVarInt(buf);
            if (count < 0 || count > 1024) {
                throw new IllegalArgumentException("DW bosstimers count is out of range: " + count);
            }

            Map<String, Long> timers = new LinkedHashMap<>(Math.max(4, count));
            for (int i = 0; i < count; i++) {
                String bossId = DwProtocolCodec.readString(buf);
                long remainingMillis = DwProtocolCodec.readLong(buf);
                timers.put(bossId, remainingMillis);
            }

            return new DwBossTimersPacket(timers);
        } finally {
            buf.release();
        }
    }

}
