package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public final class DwBossDamageDecoder {
    private DwBossDamageDecoder() {
    }

    public static DwBossDamagePacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            String bossId = DwProtocolCodec.readString(buf);
            int damage = DwProtocolCodec.readInt(buf);

            return new DwBossDamagePacket(bossId, damage);
        } finally {
            buf.release();
        }
    }
}
