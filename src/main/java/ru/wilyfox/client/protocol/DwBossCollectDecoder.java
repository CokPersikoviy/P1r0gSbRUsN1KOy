package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class DwBossCollectDecoder {
    private DwBossCollectDecoder() {
    }

    public static DwBossCollectPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int groups = DwProtocolCodec.readCollectionSize(buf);
            Map<String, Set<String>> collectibles = new LinkedHashMap<>();

            for (int i = 0; i < groups; i++) {
                String key = DwProtocolCodec.readString(buf);
                int valuesCount = DwProtocolCodec.readCollectionSize(buf);
                Set<String> values = new LinkedHashSet<>();

                for (int valueIndex = 0; valueIndex < valuesCount; valueIndex++) {
                    values.add(DwProtocolCodec.readString(buf));
                }

                collectibles.put(key, values);
            }

            return new DwBossCollectPacket(collectibles);
        } finally {
            buf.release();
        }
    }
}
