package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DwPotionCooldownsDecoder {
    private DwPotionCooldownsDecoder() {
    }

    public static DwPotionCooldownsPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int count = DwProtocolCodec.readVarInt(buf);
            Map<Integer, Long> cooldowns = new LinkedHashMap<>(Math.max(4, count));

            for (int i = 0; i < count; i++) {
                int id = DwProtocolCodec.readInt(buf);
                long remainingMillis = DwProtocolCodec.readLong(buf);
                cooldowns.put(id, remainingMillis);
            }

            return new DwPotionCooldownsPacket(cooldowns);
        } finally {
            buf.release();
        }
    }
}
