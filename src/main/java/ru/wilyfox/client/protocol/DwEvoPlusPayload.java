package ru.wilyfox.client.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DwEvoPlusPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<DwEvoPlusPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("dw", "evoplus"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DwEvoPlusPayload> STREAM_CODEC =
            CustomPacketPayload.codec(
                    (payload, buf) -> buf.writeBytes(payload.data),
                    buf -> {
                        byte[] data = new byte[buf.readableBytes()];
                        buf.readBytes(data);
                        return new DwEvoPlusPayload(data);
                    }
            );

    @Override
    public Type<DwEvoPlusPayload> type() {
        return TYPE;
    }
}
