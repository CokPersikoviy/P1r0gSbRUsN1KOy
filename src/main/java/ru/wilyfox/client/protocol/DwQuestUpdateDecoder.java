package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public final class DwQuestUpdateDecoder {
    private DwQuestUpdateDecoder() {}

    public record Update(String id, int progress) {}

    public static Update decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            Update update = new Update(DwProtocolCodec.readString(buf), DwProtocolCodec.readVarInt(buf));
            if (buf.isReadable()) throw new IllegalArgumentException("Trailing questupdate data");
            return update;
        } finally {
            buf.release();
        }
    }
}
