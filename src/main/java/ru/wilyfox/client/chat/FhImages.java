package ru.wilyfox.client.chat;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** One decoder, one opened image, no history-wide texture cache or render-thread I/O. */
public final class FhImages {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    public static final int MAX_DOWNLOAD_BYTES = GifAnimation.MAX_BYTES;
    public static final ExecutorService IO = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4), task -> {
        var thread = new Thread(task, "FH chat images"); thread.setDaemon(true); return thread;
    });
    private FhImages() {}
    public static boolean isGif(byte[] bytes) {
        return bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
    }
    static NativeImage gifPixels(GifAnimation.Frame frame) {
        var source = frame.pixels(); NativeImage result = null;
        try {
            int sample = Math.max(1, (int) Math.ceil(Math.max(source.getWidth(), source.getHeight()) / 1280d));
            int width = (source.getWidth() + sample - 1) / sample, height = (source.getHeight() + sample - 1) / sample;
            result = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                result.setPixel(x, y, source.getRGB(x * sample, y * sample));
            return result;
        } catch (RuntimeException failure) { if (result != null) result.close(); throw failure; }
        finally { source.flush(); }
    }
    public static <T> CompletableFuture<T> supply(java.util.function.Supplier<T> task) {
        try { return CompletableFuture.supplyAsync(task, IO); }
        catch (RejectedExecutionException busy) { return CompletableFuture.failedFuture(busy); }
    }
    public static byte[] read(Path path) throws IOException {
        try (var stream = Files.newInputStream(path)) {
            var bytes = stream.readNBytes(MAX_BYTES + 1); dimensions(bytes); return bytes;
        }
    }
    public static int[] dimensions(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Image exceeds size limit");
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("PNG or JPEG required");
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                String format = reader.getFormatName();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                boolean jpeg = format.equalsIgnoreCase("jpeg");
                int maxSide = jpeg ? 8192 : 4096;
                long maxPixels = jpeg ? 16_777_216 : 4_194_304;
                if (!(format.equalsIgnoreCase("png") || format.equalsIgnoreCase("jpeg")) || width <= 0 || height <= 0
                        || width > maxSide || height > maxSide || (long) width * height > maxPixels)
                    throw new IOException("PNG up to 4 megapixels or JPEG up to 16 megapixels required");
                return new int[]{width, height};
            } finally { reader.dispose(); }
        }
    }
    public static NativeImage decode(byte[] bytes) throws IOException {
        dimensions(bytes); // Check the header before allowing STB to allocate pixels.
        // NativeImage.read in 26.2 validates a PNG header, despite STB's JPEG support.
        if ((bytes[0] & 0xFF) != 0x89) {
            try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var reader = ImageIO.getImageReaders(stream).next();
                NativeImage result = null;
                try {
                    reader.setInput(stream, true, true);
                    var parameters = reader.getDefaultReadParam();
                    int sample = Math.max(1, (int) Math.ceil(Math.max(reader.getWidth(0), reader.getHeight(0)) / 1280d));
                    parameters.setSourceSubsampling(sample, sample, 0, 0);
                    var decoded = reader.read(0, parameters);
                    result = new NativeImage(decoded.getWidth(), decoded.getHeight(), false);
                    for (int y = 0; y < decoded.getHeight(); y++) for (int x = 0; x < decoded.getWidth(); x++)
                        result.setPixel(x, y, decoded.getRGB(x, y));
                    return result;
                } catch (IOException | RuntimeException failure) { if (result != null) result.close(); throw failure; }
                finally { reader.dispose(); }
            }
        }
        NativeImage image = NativeImage.read(bytes);
        int width = image.getWidth(), height = image.getHeight();
        double scale = Math.min(1, 1280d / Math.max(width, height));
        if (scale == 1) return image;
        NativeImage small = null;
        try {
            small = new NativeImage(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)), false);
            image.resizeSubRectTo(0, 0, width, height, small); return small;
        } catch (RuntimeException failure) { if (small != null) small.close(); throw failure; }
        finally { image.close(); }
    }
}
