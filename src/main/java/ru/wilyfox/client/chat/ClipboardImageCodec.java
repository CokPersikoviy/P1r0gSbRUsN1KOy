package ru.wilyfox.client.chat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Bounded in-memory conversion of Windows PNG / packed DIB clipboard data to an uploadable PNG. */
final class ClipboardImageCodec {
    static final int MAX_SOURCE_BYTES = 64 * 1024 * 1024 + 4096;
    private static final long MAX_SOURCE_PIXELS = 16_777_216;
    private static final long MAX_OUTPUT_PIXELS = 4_194_304;
    private ClipboardImageCodec() { }

    static java.nio.file.Path fileDrop(byte[] bytes) throws IOException {
        size(bytes);
        if (bytes.length < 22) throw new IOException("Invalid clipboard file list");
        var header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int start = header.getInt(0);
        boolean wide = header.getInt(16) != 0;
        if (start < 20 || start >= bytes.length || bytes.length - start > 131072 || wide && (bytes.length - start) % 2 != 0)
            throw new IOException("Invalid clipboard file list");
        var charset = wide ? java.nio.charset.StandardCharsets.UTF_16LE
                : java.nio.charset.Charset.forName(System.getProperty("native.encoding", "UTF-8"));
        String names = new String(bytes, start, bytes.length - start, charset);
        for (String name : names.split("\u0000")) {
            if (name.isEmpty()) break;
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                try { return java.nio.file.Path.of(name); }
                catch (java.nio.file.InvalidPathException bad) { throw new IOException("Invalid clipboard image path", bad); }
            }
        }
        throw new IOException("No PNG or JPEG file in clipboard");
    }

    static byte[] png(byte[] bytes) throws IOException {
        return encoded(bytes, true);
    }
    static byte[] file(java.nio.file.Path path) throws IOException {
        try (var stream = java.nio.file.Files.newInputStream(path)) {
            return encoded(stream.readNBytes(MAX_SOURCE_BYTES + 1), false);
        }
    }
    private static byte[] encoded(byte[] bytes, boolean requirePng) throws IOException {
        size(bytes);
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Invalid clipboard PNG");
            var reader = readers.next(); BufferedImage image = null;
            try {
                reader.setInput(stream, true, true);
                boolean jpeg = reader.getFormatName().equalsIgnoreCase("jpeg");
                if (!reader.getFormatName().equalsIgnoreCase("png") && (requirePng || !jpeg)) throw new IOException("PNG or JPEG required");
                int sample = sample(reader.getWidth(0), reader.getHeight(0));
                // Preserve a valid small encoded PNG, including its alpha, without recompression.
                if ((sample == 1 || jpeg) && bytes.length <= FhImages.MAX_BYTES) { FhImages.dimensions(bytes); return bytes; }
                var options = reader.getDefaultReadParam(); options.setSourceSubsampling(sample, sample, 0, 0);
                image = reader.read(0, options);
                return encode(image);
            } finally { if (image != null) image.flush(); reader.dispose(); }
        }
    }

    static byte[] dib(byte[] bytes) throws IOException {
        size(bytes);
        if (bytes.length < 40) throw new IOException("Incomplete clipboard bitmap");
        var data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int header = data.getInt(0), width = data.getInt(4), signedHeight = data.getInt(8);
        if (!(header == 40 || header == 52 || header == 56 || header == 108 || header == 124) || header > bytes.length
                || signedHeight == Integer.MIN_VALUE || data.getShort(12) != 1) throw new IOException("Invalid bitmap header");
        int height = Math.abs(signedHeight), sample = sample(width, height);
        int bits = Short.toUnsignedInt(data.getShort(14)), compression = data.getInt(16);
        if (!(bits == 16 || bits == 24 || bits == 32) || !(compression == 0 || compression == 3 || compression == 6)
                || bits == 24 && compression != 0) throw new IOException("Unsupported clipboard bitmap");
        int offset = header;
        int red = bits == 16 ? 0x7C00 : 0xFF0000, green = bits == 16 ? 0x3E0 : 0xFF00, blue = bits == 16 ? 0x1F : 0xFF, alpha = 0;
        if (compression != 0) {
            int count = compression == 6 ? 4 : 3;
            if (bytes.length < 40 + count * 4 || header != 40 && header < 40 + count * 4) throw new IOException("Missing bitmap masks");
            red = data.getInt(40); green = data.getInt(44); blue = data.getInt(48);
            if (header >= 56 || compression == 6) alpha = data.getInt(52);
            if (header == 40) offset += count * 4;
            if (!mask(red) || !mask(green) || !mask(blue) || alpha != 0 && !mask(alpha)
                    || (red & green) != 0 || (red & blue) != 0 || (green & blue) != 0
                    || (alpha & (red | green | blue)) != 0) throw new IOException("Invalid bitmap masks");
        }
        long palette = Integer.toUnsignedLong(data.getInt(32));
        if (palette > 256 || (long) offset + palette * 4 > bytes.length) throw new IOException("Invalid bitmap palette");
        offset += (int) palette * 4;
        if (header == 124 && data.getInt(112) == offset) {
            long profileSize = Integer.toUnsignedLong(data.getInt(116));
            if (offset + profileSize > bytes.length) throw new IOException("Invalid bitmap profile");
            offset += (int) profileSize;
        }
        long stride = ((long) width * bits + 31) / 32 * 4;
        if (offset + stride * height > bytes.length) throw new IOException("Truncated bitmap pixels");
        var image = new BufferedImage((width + sample - 1) / sample, (height + sample - 1) / sample, BufferedImage.TYPE_INT_ARGB);
        try {
            for (int y = 0; y < image.getHeight(); y++) {
                int sourceY = signedHeight < 0 ? y * sample : height - 1 - y * sample;
                for (int x = 0; x < image.getWidth(); x++) {
                    int p = (int) (offset + sourceY * stride + (long) x * sample * (bits / 8));
                    int pixel = bits == 32 ? data.getInt(p) : bits == 16 ? Short.toUnsignedInt(data.getShort(p))
                            : Byte.toUnsignedInt(bytes[p]) | Byte.toUnsignedInt(bytes[p + 1]) << 8 | Byte.toUnsignedInt(bytes[p + 2]) << 16;
                    image.setRGB(x, y, (alpha == 0 ? 255 : channel(pixel, alpha)) << 24
                            | channel(pixel, red) << 16 | channel(pixel, green) << 8 | channel(pixel, blue));
                }
            }
            return encode(image);
        } finally { image.flush(); }
    }
    private static boolean mask(int mask) {
        if (mask == 0) return false;
        long shifted = Integer.toUnsignedLong(mask) >>> Integer.numberOfTrailingZeros(mask);
        return (shifted & (shifted + 1)) == 0;
    }
    private static int channel(int pixel, int mask) {
        int shift = Integer.numberOfTrailingZeros(mask);
        long maximum = Integer.toUnsignedLong(mask) >>> shift;
        return (int) (((Integer.toUnsignedLong(pixel & mask) >>> shift) * 255 + maximum / 2) / maximum);
    }
    private static void size(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_SOURCE_BYTES) throw new IOException("Clipboard image too large");
    }
    private static int sample(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || (long) width * height > MAX_SOURCE_PIXELS)
            throw new IOException("Clipboard dimensions exceed limits");
        int sample = Math.max(1, (int) Math.ceil(Math.max(width, height) / 4096d));
        while ((long) ((width + sample - 1) / sample) * ((height + sample - 1) / sample) > MAX_OUTPUT_PIXELS) sample++;
        return sample;
    }
    private static byte[] encode(BufferedImage image) throws IOException {
        BufferedImage current = image;
        try {
            for (int attempt = 0; attempt < 6; attempt++) {
                var bytes = new LimitedPng(); var writer = ImageIO.getImageWritersByFormatName("png").next();
                try (var output = new MemoryCacheImageOutputStream(bytes)) {
                    writer.setOutput(output); writer.write(current); output.flush();
                    byte[] result = bytes.toByteArray(); FhImages.dimensions(result); return result;
                } catch (IOException | RuntimeException failure) {
                    if (!bytes.full) throw new IOException("Clipboard PNG conversion failed", failure);
                } finally { writer.dispose(); }
                int w = Math.max(1, current.getWidth() * 3 / 4), h = Math.max(1, current.getHeight() * 3 / 4);
                var smaller = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                    smaller.setRGB(x, y, current.getRGB(x * current.getWidth() / w, y * current.getHeight() / h));
                if (current != image) current.flush(); current = smaller;
            }
            throw new IOException("Clipboard PNG exceeds upload limit");
        } finally { if (current != image) current.flush(); }
    }
    private static final class LimitedPng extends ByteArrayOutputStream {
        private boolean full;
        @Override public synchronized void write(int value) { limit(1); super.write(value); }
        @Override public synchronized void write(byte[] bytes, int offset, int length) { limit(length); super.write(bytes, offset, length); }
        private void limit(int length) {
            if ((long) count + length > FhImages.MAX_BYTES) { full = true; throw new IllegalStateException("PNG too large"); }
        }
    }
}
