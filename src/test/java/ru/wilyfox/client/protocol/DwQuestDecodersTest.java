package ru.wilyfox.client.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class DwQuestDecodersTest {
    @Test void setupDecodesFishingAndOtherCategoriesWithVarIntProgress() {
        Map<String, DwQuest> decoded = DwQuestsSetupDecoder.decode(bytes(buf -> {
            DwProtocolCodec.writeVarInt(buf, 3);
            entry(buf, "fish", 0, 1, 148, 5_000);
            entry(buf, "hunt", 1, 0, 12, 100);
            entry(buf, "alchemy", 2, 2, 9, 20);
        }));
        assertEquals(3, decoded.size());
        assertEquals(new DwQuest("fish", DwQuest.Dimension.OVERWORLD, DwQuest.Category.FISHING,
                "Quest fish", "Lore fish", 148, 5_000), decoded.get("fish"));
        assertEquals(DwQuest.Dimension.NETHER, decoded.get("hunt").dimension());
        assertEquals(DwQuest.Category.HUNTING, decoded.get("hunt").category());
        assertEquals(DwQuest.Dimension.END, decoded.get("alchemy").dimension());
        assertEquals(DwQuest.Category.ALCHEMY, decoded.get("alchemy").category());
    }

    @Test void emptySetupIsAnEmptySnapshot() {
        assertTrue(DwQuestsSetupDecoder.decode(bytes(buf -> DwProtocolCodec.writeVarInt(buf, 0))).isEmpty());
    }

    @Test void updatePreservesLargeAndSignedProgressValues() {
        for (int progress : new int[]{148, 5_000, -1, Integer.MAX_VALUE}) {
            assertEquals(new DwQuestUpdateDecoder.Update("fish", progress), DwQuestUpdateDecoder.decode(bytes(buf -> {
                DwProtocolCodec.writeString(buf, "fish");
                DwProtocolCodec.writeVarInt(buf, progress);
            })));
        }
    }

    @Test void invalidSetupDoesNotProducePartialData() {
        for (int[] enums : new int[][]{{3, 1}, {0, 3}, {-1, 1}}) {
            assertThrows(RuntimeException.class, () -> DwQuestsSetupDecoder.decode(bytes(buf -> {
                DwProtocolCodec.writeVarInt(buf, 1);
                entry(buf, "fish", enums[0], enums[1], 0, 20);
            })));
        }
        assertThrows(RuntimeException.class, () -> DwQuestsSetupDecoder.decode(bytes(buf -> {
            DwProtocolCodec.writeVarInt(buf, 1);
            DwProtocolCodec.writeString(buf, "fish");
        })));
        assertThrows(RuntimeException.class, () -> DwQuestsSetupDecoder.decode(bytes(buf -> {
            DwProtocolCodec.writeVarInt(buf, 0);
            buf.writeByte(0);
        })));
    }

    @Test void truncatedOrTrailingUpdateIsRejected() {
        assertThrows(RuntimeException.class, () -> DwQuestUpdateDecoder.decode(bytes(buf -> DwProtocolCodec.writeString(buf, "fish"))));
        assertThrows(RuntimeException.class, () -> DwQuestUpdateDecoder.decode(bytes(buf -> {
            DwProtocolCodec.writeString(buf, "fish");
            DwProtocolCodec.writeVarInt(buf, 12);
            buf.writeByte(0);
        })));
    }

    @Test void disconnectClearsGeneralAndHourlyQuests() {
        ProtocolState state = new ProtocolState();
        state.quests.put("fish", new DwQuest("fish", DwQuest.Dimension.OVERWORLD, DwQuest.Category.FISHING, "Fish", "", 12, 20));
        state.hourlyQuestProgress.put(7, new DwHourlyQuestProgress(7, 12, Long.MAX_VALUE));
        state.resetRuntimeState();
        assertTrue(state.quests.isEmpty());
        assertTrue(state.hourlyQuestProgress.isEmpty());
    }

    private static void entry(ByteBuf buf, String id, int dimension, int category, int progress, int required) {
        DwProtocolCodec.writeString(buf, id);
        DwProtocolCodec.writeVarInt(buf, dimension);
        DwProtocolCodec.writeVarInt(buf, category);
        DwProtocolCodec.writeString(buf, "Quest " + id);
        DwProtocolCodec.writeString(buf, "Lore " + id);
        DwProtocolCodec.writeVarInt(buf, progress);
        DwProtocolCodec.writeVarInt(buf, required);
    }

    private static byte[] bytes(Consumer<ByteBuf> writer) {
        ByteBuf buf = Unpooled.buffer();
        try {
            writer.accept(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }
}
