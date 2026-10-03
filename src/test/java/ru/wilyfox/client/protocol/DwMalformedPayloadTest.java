package ru.wilyfox.client.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DwMalformedPayloadTest {
    private static final List<Function<byte[], ?>> COLLECTION_DECODERS = List.of(
            DwAbilityTypesDecoder::decode, DwAbilityTimersDecoder::decode, DwActiveRunesDecoder::decode,
            DwBossCollectDecoder::decode, DwBossTimersDecoder::decode, DwBossTypesDecoder::decode,
            DwBoostersDecoder::decode, DwClanInfoDecoder::decode, DwFishingSpotsDecoder::decode,
            DwHourlyQuestInfoDecoder::decode, DwHourlyQuestTypesDecoder::decode, DwPetTypesDecoder::decode,
            DwPotionCooldownsDecoder::decode, DwPotionTimersDecoder::decode, DwPotionTypesDecoder::decode,
            DwSellersDecoder::decode, DwSpotNibblesDecoder::decode, DwStaffTimersDecoder::decode,
            DwStaffTypesDecoder::decode, DwStatisticInfoDecoder::decode
    );

    @Test
    void collectionDecodersRejectImpossibleCountsBeforeAllocating() {
        for (int count : new int[]{-1, Integer.MAX_VALUE}) {
            byte[] payload = payload(buf -> DwProtocolCodec.writeVarInt(buf, count));
            for (var decoder : COLLECTION_DECODERS) {
                assertThrows(IllegalArgumentException.class, () -> decoder.apply(payload));
            }
        }
    }

    @Test
    void collectionDecodersStillAcceptExplicitEmptySnapshots() {
        byte[] payload = payload(buf -> DwProtocolCodec.writeVarInt(buf, 0));
        for (var decoder : COLLECTION_DECODERS) {
            assertDoesNotThrow(() -> decoder.apply(payload));
        }
    }

    @Test
    void nestedCollectionsValidateCountsBeforeAllocating() {
        byte[] booster = payload(buf -> {
            DwProtocolCodec.writeVarInt(buf, 1);
            DwProtocolCodec.writeVarInt(buf, 0);
            DwProtocolCodec.writeVarInt(buf, Integer.MAX_VALUE);
        });
        assertThrows(IllegalArgumentException.class, () -> DwBoostersDecoder.decode(booster));

        byte[] collectibles = payload(buf -> {
            DwProtocolCodec.writeVarInt(buf, 1);
            DwProtocolCodec.writeString(buf, "BOSS");
            DwProtocolCodec.writeVarInt(buf, -1);
        });
        assertThrows(IllegalArgumentException.class, () -> DwBossCollectDecoder.decode(collectibles));
    }

    @Test
    void stringListsRequireAtLeastOneBytePerEntry() {
        byte[] truncated = payload(buf -> {
            DwProtocolCodec.writeVarInt(buf, 2);
            DwProtocolCodec.writeString(buf, "");
        });
        assertThrows(IllegalArgumentException.class, () -> DwActiveRunesDecoder.decode(truncated));

        byte[] emptyNames = payload(buf -> {
            DwProtocolCodec.writeVarInt(buf, 2);
            DwProtocolCodec.writeString(buf, "");
            DwProtocolCodec.writeString(buf, "");
        });
        assertEquals(List.of("", ""), DwActiveRunesDecoder.decode(emptyNames).runes());
    }

    @Test
    void overflowingPackedValuesCannotWrapIntoValidNumbers() {
        assertInvalidPackedInteger(new int[]{0x80, 0x80, 0x80, 0x80, 0x10}, DwProtocolCodec::readVarInt);
        assertInvalidPackedInteger(new int[]{0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x02},
                DwProtocolCodec::readPackedLong);
        assertInvalidPackedInteger(new int[]{0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80},
                DwProtocolCodec::readPackedLong);
    }

    @Test
    void signedVarIntsKeepTheirExistingWireRepresentation() {
        for (int value : new int[]{0, 127, 128, Integer.MIN_VALUE, Integer.MAX_VALUE, -1}) {
            ByteBuf buf = Unpooled.buffer();
            try {
                DwProtocolCodec.writeVarInt(buf, value);
                assertEquals(value, DwProtocolCodec.readVarInt(buf));
            } finally {
                buf.release();
            }
        }
    }

    private static void assertInvalidPackedInteger(int[] decodedBytes, Function<ByteBuf, ?> decoder) {
        ByteBuf buf = Unpooled.buffer();
        try {
            for (int value : decodedBytes) {
                buf.writeByte(value ^ 103);
            }
            assertThrows(IllegalArgumentException.class, () -> decoder.apply(buf));
        } finally {
            buf.release();
        }
    }

    private static byte[] payload(Consumer<ByteBuf> writer) {
        ByteBuf buf = Unpooled.buffer();
        try {
            writer.accept(buf);
            byte[] result = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), result);
            return result;
        } finally {
            buf.release();
        }
    }
}
