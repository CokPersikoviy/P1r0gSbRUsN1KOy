package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.warn;

final class ProtocolDebugLogger {
    private static final int HEX_PREVIEW_BYTES = 128;
    private static final int UTF_PREVIEW_CHARS = 180;

    private ProtocolDebugLogger() {
    }

    static void logPayloadSampleIfNeeded(ProtocolState state, String subchannel, byte[] data) {
        int seen = state.payloadSampleCounts.getOrDefault(subchannel, 0);
        if (seen >= ProtocolState.PAYLOAD_SAMPLE_LIMIT_PER_SUBCHANNEL) {
            return;
        }

        int sampleIndex = seen + 1;
        state.payloadSampleCounts.put(subchannel, sampleIndex);
        logPayloadStructure("sample #" + sampleIndex, subchannel, data, null, false);
    }

    static void logUnknownPayload(String reason, byte[] data) {
        logPayloadStructure(reason, null, data, null, true);
    }

    static void logUnknownPayloadBody(String reason, String subchannel, byte[] body) {
        logPayloadStructure(reason, subchannel, body, null, false);
    }

    static void logDecodeFailure(String subchannel, byte[] data, Exception exception) {
        logPayloadStructure("decode failure", subchannel, data, exception, false);
    }

    private static void logPayloadStructure(String reason, String subchannel, byte[] data, Exception exception, boolean decodeEnvelope) {
        PayloadBody body = decodeEnvelope ? extractEnvelopeBody(data) : PayloadBody.raw(data);
        String label = subchannel == null ? "<unknown>" : subchannel;

        warn(
                LOGGER,
                "DW protocol: {} for subchannel={}, totalBytes={}, bodyBytes={}, bodyUtfPreview={}, bodyHexPreview={}",
                reason,
                label,
                data.length,
                body.body.length,
                utfPreview(body.body),
                hexPreview(body.body)
        );

        String numericPreview = numericPreview(body.body);
        if (numericPreview != null) {
            warn(LOGGER, "DW protocol: {} numeric preview for subchannel={} -> {}", reason, label, numericPreview);
        }

        if (body.subchannelBytes >= 0) {
            warn(
                    LOGGER,
                    "DW protocol: {} framing for subchannel={}, subchannelBytes={}, trailingBytesAfterBody={}",
                    reason,
                    label,
                    body.subchannelBytes,
                    body.trailingBytes
            );
        }

        if (exception != null) {
            warn(LOGGER, "DW protocol: {} exception for subchannel={}: {}", reason, label, exception.toString());
        }
    }

    private static PayloadBody extractEnvelopeBody(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            buf.readUtf();
            int bodyStart = buf.readerIndex();
            int readable = buf.readableBytes();
            byte[] body = new byte[readable];
            buf.readBytes(body);
            return new PayloadBody(bodyStart, body, buf.readableBytes());
        } catch (Exception ignored) {
            return PayloadBody.raw(data);
        } finally {
            buf.release();
        }
    }

    private static String utfPreview(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\u0000', ' ')
                .trim();
        if (text.isEmpty()) {
            return "<blank>";
        }

        return text.length() > UTF_PREVIEW_CHARS ? text.substring(0, UTF_PREVIEW_CHARS) + "..." : text;
    }

    private static String hexPreview(byte[] data) {
        if (data.length == 0) {
            return "<empty>";
        }

        int size = Math.min(data.length, HEX_PREVIEW_BYTES);
        String preview = HexFormat.of().withUpperCase().formatHex(data, 0, size);
        return data.length > size ? preview + "..." : preview;
    }

    private static String numericPreview(byte[] data) {
        if (data.length == 0) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        appendAttempt(builder, "varInt", attemptReadVarInt(data));
        appendAttempt(builder, "int", attemptReadInt(data));
        appendAttempt(builder, "long", attemptReadLong(data));
        appendAttempt(builder, "double", attemptReadDouble(data));
        appendAttempt(builder, "boolean", attemptReadBoolean(data));
        appendAttempt(builder, "utf", attemptReadUtf(data));
        return builder.isEmpty() ? null : builder.toString();
    }

    private static void appendAttempt(StringBuilder builder, String label, String value) {
        if (value == null) {
            return;
        }

        if (!builder.isEmpty()) {
            builder.append(", ");
        }
        builder.append(label).append('=').append(value);
    }

    private static String attemptReadVarInt(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            return Integer.toString(buf.readVarInt());
        } catch (Exception ignored) {
            return null;
        } finally {
            buf.release();
        }
    }

    private static String attemptReadInt(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            return Integer.toString(buf.readInt());
        } catch (Exception ignored) {
            return null;
        } finally {
            buf.release();
        }
    }

    private static String attemptReadLong(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            return Long.toString(buf.readLong());
        } catch (Exception ignored) {
            return null;
        } finally {
            buf.release();
        }
    }

    private static String attemptReadDouble(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            return Double.toString(buf.readDouble());
        } catch (Exception ignored) {
            return null;
        } finally {
            buf.release();
        }
    }

    private static String attemptReadBoolean(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            return Boolean.toString(buf.readBoolean());
        } catch (Exception ignored) {
            return null;
        } finally {
            buf.release();
        }
    }

    private static String attemptReadUtf(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            String value = buf.readUtf();
            return '"' + (value.length() > 80 ? value.substring(0, 80) + "..." : value) + '"';
        } catch (Exception ignored) {
            return null;
        } finally {
            buf.release();
        }
    }

    private record PayloadBody(int subchannelBytes, byte[] body, int trailingBytes) {
        private static PayloadBody raw(byte[] data) {
            return new PayloadBody(-1, data.clone(), 0);
        }
    }
}
