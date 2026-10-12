package ru.wilyfox.client.chat;

import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.w3c.dom.Node;

/** Sequential GIF composition: one canvas, one optional restore canvas, never a cache of all frames. */
public final class GifAnimation implements AutoCloseable {
    public static final int MAX_BYTES = 8 * 1024 * 1024, MAX_PIXELS = 1_048_576, MAX_FRAMES = 600;
    public record Frame(BufferedImage pixels, int delayMillis) {}
    private final ReentrantLock lock = new ReentrantLock();
    private final ImageReader reader;
    private final MemoryCacheImageInputStream input;
    private BufferedImage canvas, restore;
    private final int frameCount, width, height, background;
    private int index;
    private Info previous;
    private volatile boolean closed;
    private boolean disposed;
    private record Info(int x, int y, int width, int height, String disposal, int delay, int clearColor) {}

    public GifAnimation(byte[] bytes) throws IOException {
        if (!FhImages.isGif(bytes) || bytes.length > MAX_BYTES) throw new IOException("Invalid GIF size");
        reader = ImageIO.getImageReadersByFormatName("gif").next();
        input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes));
        try {
            reader.setInput(input, false, false);
            Node metadata = reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0");
            Node descriptor = child(metadata, "LogicalScreenDescriptor");
            width = integer(descriptor, "logicalScreenWidth", 0); height = integer(descriptor, "logicalScreenHeight", 0);
            if (width <= 0 || height <= 0 || width > 1920 || height > 1920 || (long) width * height > MAX_PIXELS)
                throw new IOException("GIF exceeds canvas limit");
            frameCount = reader.getNumImages(true);
            if (frameCount < 1 || frameCount > MAX_FRAMES) throw new IOException("GIF exceeds frame limit");
            Node colors = child(metadata, "GlobalColorTable");
            int backgroundIndex = integer(colors, "backgroundColorIndex", -1), color = 0;
            if (colors != null) for (Node n = colors.getFirstChild(); n != null; n = n.getNextSibling())
                if (integer(n, "index", -2) == backgroundIndex) color = 0xFF000000 | integer(n, "red", 0) << 16
                        | integer(n, "green", 0) << 8 | integer(n, "blue", 0);
            background = color;
            canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        } catch (IOException | RuntimeException failure) {
            reader.dispose(); input.close(); throw failure;
        }
    }
    public Frame nextFrame() throws IOException {
        lock.lock();
        try {
            if (closed) throw new IOException("GIF closed");
            if (index == frameCount) { index = 0; previous = null; restore = null; }
            var tree = reader.getImageMetadata(index).getAsTree("javax_imageio_gif_image_1.0");
            Node descriptor = child(tree, "ImageDescriptor"), control = child(tree, "GraphicControlExtension");
            boolean transparent = "TRUE".equalsIgnoreCase(attribute(control, "transparentColorFlag", "FALSE"));
            int clear = transparent ? 0 : background;
            var info = new Info(integer(descriptor, "imageLeftPosition", 0), integer(descriptor, "imageTopPosition", 0),
                    integer(descriptor, "imageWidth", 0), integer(descriptor, "imageHeight", 0),
                    attribute(control, "disposalMethod", "none"), Math.clamp(integer(control, "delayTime", 10) * 10, 40, 10_000), clear);
            if (info.width <= 0 || info.height <= 0 || info.x < 0 || info.y < 0
                    || (long) info.x + info.width > width || (long) info.y + info.height > height)
                throw new IOException("GIF frame exceeds canvas");
            if (index == 0) fill(0, 0, width, height, clear);
            else if (previous != null) {
                if (previous.disposal.equals("restoreToBackgroundColor"))
                    fill(previous.x, previous.y, previous.width, previous.height, previous.clearColor);
                else if (previous.disposal.equals("restoreToPrevious") && restore != null) {
                    var graphics = canvas.createGraphics();
                    try { graphics.setComposite(AlphaComposite.Src); graphics.drawImage(restore, 0, 0, null); }
                    finally { graphics.dispose(); }
                }
            }
            restore = info.disposal.equals("restoreToPrevious") ? copy(canvas) : null;
            var raw = reader.read(index);
            try {
                var graphics = canvas.createGraphics();
                try { graphics.drawImage(raw, info.x, info.y, null); }
                finally { graphics.dispose(); }
            } finally { raw.flush(); }
            previous = info; index++;
            return new Frame(copy(canvas), info.delay);
        } finally {
            if (closed) dispose();
            lock.unlock();
            if (closed) close(); // Covers a close racing between the final check and unlocking.
        }
    }
    private void fill(int x, int y, int w, int h, int argb) {
        var graphics = canvas.createGraphics();
        try { graphics.setComposite(AlphaComposite.Src); graphics.setColor(new java.awt.Color(argb, true)); graphics.fillRect(x, y, w, h); }
        finally { graphics.dispose(); }
    }
    private static BufferedImage copy(BufferedImage source) {
        var result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = result.createGraphics();
        try { graphics.setComposite(AlphaComposite.Src); graphics.drawImage(source, 0, 0, null); }
        finally { graphics.dispose(); }
        return result;
    }
    private static Node child(Node node, String name) {
        if (node != null) for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) if (n.getNodeName().equals(name)) return n;
        return null;
    }
    private static String attribute(Node node, String key, String fallback) {
        if (node == null || node.getAttributes() == null || node.getAttributes().getNamedItem(key) == null) return fallback;
        return node.getAttributes().getNamedItem(key).getNodeValue();
    }
    private static int integer(Node node, String key, int fallback) {
        try { return Integer.parseInt(attribute(node, key, Integer.toString(fallback))); }
        catch (NumberFormatException bad) { return fallback; }
    }
    @Override public void close() {
        closed = true;
        // Closing the screen never waits for a decoder on the render thread.
        if (lock.tryLock()) { try { dispose(); } finally { lock.unlock(); } }
    }
    private void dispose() {
        if (disposed) return; disposed = true; reader.dispose();
        try { input.close(); } catch (IOException ignored) {}
        if (canvas != null) canvas.flush(); if (restore != null) restore.flush();
        canvas = restore = null;
    }
}
