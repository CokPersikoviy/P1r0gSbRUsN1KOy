package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ClipboardImageCodecTest {
    @Test void copiedImageFilesUseWindowsUnicodeFileLists() throws Exception {
        byte[] names = "D:\\картинки\\пример.png\0\0".getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
        var data = ByteBuffer.allocate(20 + names.length).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(0, 20); data.putInt(16, 1); data.position(20); data.put(names);
        assertEquals(java.nio.file.Path.of("D:\\картинки\\пример.png"), ClipboardImageCodec.fileDrop(data.array()));
        data.putInt(0, 200_000);
        assertThrows(IOException.class, () -> ClipboardImageCodec.fileDrop(data.array()));
    }
    @Test void windowsBottomUp32BitRgbDoesNotTurnZeroAlphaScreenshotsTransparent() throws Exception {
        var bitmap = bitmap(40, 1, 2, 32, 0, 8);
        bitmap.putInt(40, 0x000000FF); bitmap.putInt(44, 0x00FF0000);
        var image = decode(ClipboardImageCodec.dib(bitmap.array()));
        assertEquals(0xFFFF0000, image.getRGB(0, 0));
        assertEquals(0xFF0000FF, image.getRGB(0, 1));
    }
    @Test void topDown24BitRowsHonorPaddingAndV5BitfieldsPreserveTransparency() throws Exception {
        var rgb = bitmap(40, 1, -2, 24, 0, 8);
        rgb.put(42, (byte) 255); rgb.put(44, (byte) 255);
        var image = decode(ClipboardImageCodec.dib(rgb.array()));
        assertEquals(0xFFFF0000, image.getRGB(0, 0)); assertEquals(0xFF0000FF, image.getRGB(0, 1));
        var rgba = bitmap(124, 2, -1, 32, 3, 8);
        rgba.putInt(40, 0xFF0000); rgba.putInt(44, 0xFF00); rgba.putInt(48, 0xFF); rgba.putInt(52, 0xFF000000);
        rgba.putInt(124, 0x80FF0000); rgba.putInt(128, 0x000000FF);
        image = decode(ClipboardImageCodec.dib(rgba.array()));
        assertEquals(0x80FF0000, image.getRGB(0, 0)); assertEquals(0x000000FF, image.getRGB(1, 0));
    }
    @Test void invalidHeadersAndTruncatedPixelsAreRejectedBeforePixelAllocation() {
        assertThrows(IOException.class, () -> ClipboardImageCodec.dib(new byte[16]));
        assertThrows(IOException.class, () -> ClipboardImageCodec.dib(bitmap(40, 200_000, 1, 32, 0, 4).array()));
        assertThrows(IOException.class, () -> ClipboardImageCodec.dib(bitmap(40, 1, Integer.MIN_VALUE, 32, 0, 4).array()));
        assertThrows(IOException.class, () -> ClipboardImageCodec.dib(bitmap(40, 4, 4, 24, 0, 4).array()));
        assertThrows(IOException.class, () -> ClipboardImageCodec.dib(bitmap(40, 1, 1, 32, 1, 4).array()));
        var masks = bitmap(56, 1, 1, 32, 3, 4);
        masks.putInt(40, 0xFF); masks.putInt(44, 0xFF); masks.putInt(48, 0xFF0000);
        assertThrows(IOException.class, () -> ClipboardImageCodec.dib(masks.array()));
    }
    @Test void validPngIsPreservedAndIncompressibleImageShrinksToTheUploadByteLimit() throws Exception {
        var small = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        small.setRGB(0, 0, 0x80FF0000);
        byte[] original = png(small);
        assertArrayEquals(original, ClipboardImageCodec.png(original));
        var random = new java.util.Random(17);
        var noise = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 1024; y++) for (int x = 0; x < 1024; x++) noise.setRGB(x, y, random.nextInt());
        byte[] large = png(noise); assertTrue(large.length > FhImages.MAX_BYTES);
        byte[] converted = ClipboardImageCodec.png(large);
        assertTrue(converted.length <= FhImages.MAX_BYTES);
        assertTrue(decode(converted).getWidth() < 1024);
    }
    @Test void oversizedBitmapIsSubsampledToTheOutputPixelLimit() throws Exception {
        var raw = bitmap(40, 4096, 2048, 24, 0, 4096 * 2048 * 3);
        byte[] converted = ClipboardImageCodec.dib(raw.array());
        int[] size = FhImages.dimensions(converted);
        assertEquals(2048, size[0]); assertEquals(1024, size[1]);
    }
    private static ByteBuffer bitmap(int header, int w, int h, int bits, int compression, int pixels) {
        var result = ByteBuffer.allocate(header + pixels).order(ByteOrder.LITTLE_ENDIAN);
        result.putInt(0, header); result.putInt(4, w); result.putInt(8, h);
        result.putShort(12, (short) 1); result.putShort(14, (short) bits); result.putInt(16, compression);
        return result;
    }
    private static BufferedImage decode(byte[] bytes) throws IOException { return ImageIO.read(new ByteArrayInputStream(bytes)); }
    private static byte[] png(BufferedImage image) throws IOException {
        var output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output); return output.toByteArray();
    }
}
