package ru.wilyfox.client.protocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
/** Executes only the HWID and buffer utilities from the supplied reference, offline. */
@EnabledIfSystemProperty(named = "froghelper.evoReference", matches = ".+")
class EvoReferenceCompatibilityTest {
    private URLClassLoader referenceLoader() throws Exception {
        return new URLClassLoader(new java.net.URL[]{Path.of(System.getProperty("froghelper.evoReference")).toUri().toURL()}, getClass().getClassLoader());
    }
    @Test void fingerprintMatchesOriginal332() throws Exception {
        try (var loader = referenceLoader()) {
            Class<?> hw = loader.loadClass("SlADkIYpErS1koVIYsoK$232445035.sLADKIyPERSiK0V1ys0K$696881169");
            Object instance = hw.getConstructor().newInstance();
            assertEquals(hw.getMethod("sLAdkiyp3RsIk0vIYsok$444024895").invoke(instance), DwHandshakeFingerprint.generate());
        }
    }
    @Test void handshakeStringsMatchOriginal332Bytes() throws Exception {
        try (var loader = referenceLoader()) {
            Class<?> codec = loader.loadClass("SladK1yp3rsIKOVIys0k$5617715.SLADkiypErs1KoVIYS0k$245056466");
            for (String value : Arrays.asList("", "a".repeat(64), "РџСЂРёРІРµС‚ рџђё", "x".repeat(300))) {
                ByteBuf expected = Unpooled.buffer(), actual = Unpooled.buffer();
                try {
                    codec.getMethod("slADkIYPERS1k0v1ysOk$848667687", ByteBuf.class, String.class).invoke(null, expected, value);
                    DwProtocolCodec.writeString(actual, value);
                    byte[] first = new byte[expected.readableBytes()], second = new byte[actual.readableBytes()];
                    expected.readBytes(first); actual.readBytes(second);
                    assertArrayEquals(first, second);
                } finally { expected.release(); actual.release(); }
            }
        }
    }
    @Test void tokenNullableStringsMatchOriginal332() throws Exception {
        try (var loader = referenceLoader()) {
            Class<?> codec = loader.loadClass("SladK1yp3rsIKOVIys0k$5617715.SLADkiypErs1KoVIYS0k$245056466");
            for (String value : Arrays.asList(null, "", "header.claims.signature")) {
                ByteBuf expected = Unpooled.buffer();
                try {
                    codec.getMethod("SLADK1yPeRS1kov1ySoK$902612184", ByteBuf.class, String.class).invoke(null, expected, value);
                    byte[] bytes = new byte[expected.readableBytes()]; expected.readBytes(bytes);
                    assertEquals(value, DwTokenDecoder.decode(bytes).value());
                } finally { expected.release(); }
            }
        }
    }
    @Test void numericMasksMatchOriginal332() throws Exception {
        try (var loader = referenceLoader()) {
            Class<?> codec = loader.loadClass("SladK1yp3rsIKOVIys0k$5617715.SLADkiypErs1KoVIYS0k$245056466");
            ByteBuf buffer = Unpooled.buffer();
            try {
                for (int value : new int[]{0, -1, 127, 128, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                    codec.getMethod("SlADKIypErs1k0viYs0k$645885856", ByteBuf.class, int.class).invoke(null, buffer, value);
                    assertEquals(value, DwProtocolCodec.readInt(buffer));
                }
                for (long value : new long[]{0L, -1L, Long.MAX_VALUE, Long.MIN_VALUE}) {
                    codec.getMethod("slaDK1yP3rsiKOv1ySok$951800128", ByteBuf.class, long.class).invoke(null, buffer, value);
                    assertEquals(value, DwProtocolCodec.readLong(buffer));
                }
                for (double value : new double[]{0, -0.0, 123.5, Double.POSITIVE_INFINITY}) {
                    codec.getMethod("SLadKiyp3RSiKoV1ySOk$899054120", ByteBuf.class, double.class).invoke(null, buffer, value);
                    assertEquals(value, DwProtocolCodec.readDouble(buffer));
                }
            } finally { buffer.release(); }
        }
    }
}
