package ru.wilyfox.client.chat;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossShareCodecTest {
    @Test
    void importsACompleteShare() {
        byte[] compressed = share(buf -> {
            buf.writeVarInt(2);
            buf.writeVarInt(25);
            buf.writeVarInt(60);
            buf.writeVarInt(27);
            buf.writeVarInt(120);
        });

        assertEquals(Map.of(25, 60_000L, 27, 120_000L),
                BossShareService.decodeBosses(encoded(compressed)));
    }

    @Test
    void neverImportsTimersFromATruncatedCompressedStream() {
        byte[] complete = share(buf -> {
            buf.writeVarInt(1);
            buf.writeVarInt(25);
            buf.writeVarInt(60);
        });

        // Removing the checksum leaves all timer bytes available but the stream incomplete.
        byte[] truncated = Arrays.copyOf(complete, complete.length - 2);
        assertTrue(BossShareService.decodeBosses(encoded(truncated)).isEmpty());
    }

    @Test
    void neverImportsAPartialDeclaredSnapshot() {
        byte[] truncatedList = share(buf -> {
            buf.writeVarInt(2);
            buf.writeVarInt(25);
            buf.writeVarInt(60);
        });

        assertTrue(BossShareService.decodeBosses(encoded(truncatedList)).isEmpty());
    }

    private static String encoded(byte[] compressed) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);
    }

    private static byte[] share(Consumer<FriendlyByteBuf> entries) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        byte[] raw;
        try {
            buf.writeByte(1);
            entries.accept(buf);
            raw = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), raw);
        } finally {
            buf.release();
        }

        Deflater deflater = new Deflater();
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            byte[] output = new byte[64];
            while (!deflater.finished()) {
                int count = deflater.deflate(output);
                compressed.write(output, 0, count);
            }
            return compressed.toByteArray();
        } finally {
            deflater.end();
        }
    }
}
