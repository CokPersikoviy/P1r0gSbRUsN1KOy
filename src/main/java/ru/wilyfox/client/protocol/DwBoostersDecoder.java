package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import ru.wilyfox.client.booster.BoosterStore;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DwBoostersDecoder {
    private enum RemoteKind {
        SHARD,
        MONEY,
        SHAFT
    }

    private DwBoostersDecoder() {
    }

    public static DwBoostersPacket decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            int groupCount = DwProtocolCodec.readVarInt(buf);
            Map<BoosterStore.Kind, List<BoosterStore.ProtocolEntry>> boosters = new EnumMap<>(BoosterStore.Kind.class);

            for (int i = 0; i < groupCount; i++) {
                RemoteKind remoteKind = RemoteKind.values()[DwProtocolCodec.readVarInt(buf)];
                int entryCount = DwProtocolCodec.readVarInt(buf);
                List<BoosterStore.ProtocolEntry> entries = new ArrayList<>(Math.max(0, entryCount));

                for (int j = 0; j < entryCount; j++) {
                    long remainingMillis = DwProtocolCodec.readLong(buf);
                    double multiplier = DwProtocolCodec.readDouble(buf);
                    entries.add(new BoosterStore.ProtocolEntry(multiplier, remainingMillis));
                }

                BoosterStore.Kind kind = switch (remoteKind) {
                    case MONEY -> BoosterStore.Kind.MONEY;
                    case SHARD -> BoosterStore.Kind.SHARD;
                    case SHAFT -> BoosterStore.Kind.SHAFT;
                };
                boosters.put(kind, entries);
            }

            return new DwBoostersPacket(boosters);
        } finally {
            buf.release();
        }
    }
}
