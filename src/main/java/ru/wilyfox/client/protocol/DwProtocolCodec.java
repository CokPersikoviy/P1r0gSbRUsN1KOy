package ru.wilyfox.client.protocol;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

final class DwProtocolCodec {
    private static final int BYTE_XOR_KEY = 103;
    private static final int SEGMENT_MASK = 0x7F;
    private static final int CONTINUATION_MASK = 0x80;
    private static final int MAX_STRING_BYTES = 32_767;
    private static final int INT_XOR_MASK = -2027096677 - 533040692;
    private static final long LONG_XOR_MASK = 7020125408405881293L - (-430912393916016026L);
    private static final long DOUBLE_XOR_MASK = 7094196544379894642L - (-356841257942002677L);

    private DwProtocolCodec() {
    }

    static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);

        for (int i = 0; i < bytes.length; i++) {
            buf.writeByte((bytes[i] & 0xFF) ^ (BYTE_XOR_KEY ^ (i & 0xFF)));
        }
    }

    static String readString(ByteBuf buf) {
        int length = readVarInt(buf);
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("DW string length is out of range: " + length);
        }
        if (length > buf.readableBytes()) {
            throw new IllegalArgumentException("DW string length exceeds readable bytes: " + length + " > " + buf.readableBytes());
        }

        byte[] bytes = new byte[length];

        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (buf.readByte() ^ (BYTE_XOR_KEY ^ (i & 0xFF)));
        }

        return new String(bytes, StandardCharsets.UTF_8);
    }

    static int readInt(ByteBuf buf) {
        return buf.readInt() ^ INT_XOR_MASK;
    }

    static long readLong(ByteBuf buf) {
        return buf.readLong() ^ LONG_XOR_MASK;
    }

    static long readPackedLong(ByteBuf buf) {
        long value = 0L;

        for (int position = 0; position < 10; position++) {
            int currentByte = buf.readByte() ^ BYTE_XOR_KEY;
            value |= (long) (currentByte & SEGMENT_MASK) << (position * 7);

            if ((currentByte & CONTINUATION_MASK) == 0) {
                return value;
            }
        }

        return value;
    }

    static double readDouble(ByteBuf buf) {
        return Double.longBitsToDouble(buf.readLong() ^ DOUBLE_XOR_MASK);
    }

    static boolean readBoolean(ByteBuf buf) {
        return (buf.readByte() ^ BYTE_XOR_KEY) != 0;
    }

    static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~SEGMENT_MASK) != 0) {
            buf.writeByte(((value & SEGMENT_MASK) | CONTINUATION_MASK) ^ BYTE_XOR_KEY);
            value >>>= 7;
        }

        buf.writeByte(value ^ BYTE_XOR_KEY);
    }

    static int readVarInt(ByteBuf buf) {
        int value = 0;
        int position = 0;

        while (true) {
            if (position >= 32) {
                throw new IllegalArgumentException("DW varint is too large");
            }

            int currentByte = buf.readByte() ^ BYTE_XOR_KEY;
            value |= (currentByte & SEGMENT_MASK) << position;

            if ((currentByte & CONTINUATION_MASK) == 0) {
                return value;
            }

            position += 7;
        }
    }
}
