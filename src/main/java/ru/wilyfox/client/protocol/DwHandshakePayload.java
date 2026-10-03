package ru.wilyfox.client.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DwHandshakePayload(String fingerprint) implements CustomPacketPayload {
    public static final Type<DwHandshakePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("dw", "handshake"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DwHandshakePayload> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (payload, buf) -> DwProtocolCodec.writeString(buf, payload.fingerprint),
                    buf -> new DwHandshakePayload(DwProtocolCodec.readString(buf))
            );

    @Override
    public Type<DwHandshakePayload> type() {
        return TYPE;
    }
}
